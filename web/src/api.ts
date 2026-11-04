// API client for the BFF. Relative URLs only: the Vite dev proxy (and CloudFront
// in production) routes /api to the BFF, so there is no CORS.

import type { FieldErrors, QuoteField, QuoteRequest } from './validation';

export interface Quote {
  quoteId: string;
  commissionRate: number;
  totalCommission: number;
}

export type QuoteResult =
  | { kind: 'success'; quote: Quote }
  | {
      kind: 'error';
      message: string;
      retryable: boolean;
      correlationId?: string;
      fieldErrors?: FieldErrors;
    };

export interface CreateQuoteOptions {
  /** Dev token for the Authorization header; omitted when undefined. */
  token?: string;
  /** Sent on every attempt of this submission, including Try again. */
  idempotencyKey: string;
  timeoutMs?: number;
}

export type CreateQuote = (request: QuoteRequest, options: CreateQuoteOptions) => Promise<QuoteResult>;

/** Above the BFF's 20 second deadline, so the BFF normally answers first. */
const CLIENT_TIMEOUT_MS = 25_000;

const GENERIC_MESSAGE = "Sorry, we couldn't get a quote right now.";
const TIMEOUT_MESSAGE = 'Getting a quote is taking too long.';

// Generic messages per status. The cause is never shown; the reference lets support find it.
const MESSAGES: Record<number, string> = {
  400: 'Please check the highlighted fields.',
  401: 'You need to sign in to get a quote.',
  403: "You don't have permission to create quotes.",
};

const FORM_FIELDS: readonly string[] = ['loanAmount', 'loanTermInMonths', 'riskBand'];

/** Never throws: every outcome, including timeouts and network errors, is a QuoteResult. */
export const createQuote: CreateQuote = async (request, { token, idempotencyKey, timeoutMs = CLIENT_TIMEOUT_MS }) => {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
    };
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
    const response = await fetch('/api/v1/quotes', {
      method: 'POST',
      headers,
      body: JSON.stringify(request),
      signal: controller.signal,
    });
    if (response.ok) {
      return { kind: 'success', quote: (await response.json()) as Quote };
    }
    return problemResult(response.status, await readJson(response));
  } catch {
    return {
      kind: 'error',
      message: controller.signal.aborted ? TIMEOUT_MESSAGE : GENERIC_MESSAGE,
      retryable: true,
    };
  } finally {
    clearTimeout(timer);
  }
};

/** Reads a ProblemDetail body: retryable, correlationId and field errors. */
function problemResult(status: number, body: Record<string, unknown>): QuoteResult {
  return {
    kind: 'error',
    message: MESSAGES[status] ?? GENERIC_MESSAGE,
    // Without a ProblemDetail (for example the proxy can't reach the BFF), only 5xx is worth retrying.
    retryable: typeof body.retryable === 'boolean' ? body.retryable : status >= 500,
    correlationId: typeof body.correlationId === 'string' ? body.correlationId : undefined,
    fieldErrors: fieldErrors(body.errors),
  };
}

function fieldErrors(errors: unknown): FieldErrors | undefined {
  if (!Array.isArray(errors)) {
    return undefined;
  }
  const result: FieldErrors = {};
  for (const error of errors) {
    if (FORM_FIELDS.includes(error?.field) && typeof error?.message === 'string') {
      result[error.field as QuoteField] = error.message;
    }
  }
  return Object.keys(result).length > 0 ? result : undefined;
}

async function readJson(response: Response): Promise<Record<string, unknown>> {
  try {
    const body = await response.json();
    return body !== null && typeof body === 'object' ? body : {};
  } catch {
    return {};
  }
}
