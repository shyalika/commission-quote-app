// Wires the form: validation, one Idempotency-Key per submission, loading and
// "Still working…", and the result or error.

import type { CreateQuote } from './api';
import {
  focusFirstInvalid,
  hideTryAgain,
  renderForm,
  showError,
  showFieldErrors,
  showLoading,
  showResult,
  showStillWorking,
  type QuoteView,
} from './render';
import { validateQuoteForm, type QuoteFormValues, type QuoteRequest } from './validation';

const STILL_WORKING_AFTER_MS = 3_000;

export interface AppOptions {
  createQuote: CreateQuote;
  showDevSelector: boolean;
}

export function mountQuoteApp(root: HTMLElement, { createQuote, showDevSelector }: AppOptions): void {
  const view = renderForm(root, { showDevSelector });

  // One key per submission: Try again reuses it, editing any field clears it.
  let idempotencyKey: string | null = null;
  let lastRequest: QuoteRequest | null = null;
  let inFlight = false;

  async function send(request: QuoteRequest, key: string): Promise<void> {
    inFlight = true;
    showLoading(view);
    const stillWorking = setTimeout(() => showStillWorking(view), STILL_WORKING_AFTER_MS);
    try {
      const result = await createQuote(request, { token: view.signedInAs?.value, idempotencyKey: key });
      if (result.kind === 'success') {
        showResult(view, result.quote);
      } else {
        if (result.fieldErrors) {
          showFieldErrors(view, result.fieldErrors);
        }
        showError(view, result);
      }
    } finally {
      clearTimeout(stillWorking);
      inFlight = false;
    }
  }

  view.form.addEventListener('submit', (event) => {
    event.preventDefault();
    if (inFlight) {
      return;
    }
    const validation = validateQuoteForm(readValues(view));
    showFieldErrors(view, validation.ok ? {} : validation.errors);
    if (!validation.ok) {
      focusFirstInvalid(view, validation.errors);
      return;
    }
    lastRequest = validation.request;
    idempotencyKey ??= crypto.randomUUID();
    void send(lastRequest, idempotencyKey);
  });

  view.tryAgain.addEventListener('click', () => {
    if (!inFlight && lastRequest && idempotencyKey) {
      void send(lastRequest, idempotencyKey);
    }
  });

  // Any edit, including switching the dev user, makes the next submit a new submission.
  const startNewSubmission = () => {
    idempotencyKey = null;
    hideTryAgain(view);
  };
  view.form.addEventListener('input', startNewSubmission);
  view.form.addEventListener('change', startNewSubmission);
}

function readValues(view: QuoteView): QuoteFormValues {
  return {
    loanAmount: view.fields.loanAmount.value,
    loanTermInMonths: view.fields.loanTermInMonths.value,
    riskBand: view.fields.riskBand.value,
  };
}
