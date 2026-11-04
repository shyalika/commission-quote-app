// Form validation, matching the BFF's rules (design section 6). The BFF stays
// the source of truth; this gives fast feedback in the browser.

export type RiskBand = 'LOW' | 'MEDIUM' | 'HIGH';

/** The raw values typed into the form. */
export interface QuoteFormValues {
  loanAmount: string;
  loanTermInMonths: string;
  riskBand: string;
}

export type QuoteField = keyof QuoteFormValues;

export type FieldErrors = Partial<Record<QuoteField, string>>;

/** A request the BFF should accept. */
export interface QuoteRequest {
  loanAmount: number;
  loanTermInMonths: number;
  riskBand: RiskBand;
}

export type ValidationResult =
  | { ok: true; request: QuoteRequest }
  | { ok: false; errors: FieldErrors };

const NUMBER = /^-?\d+(\.\d+)?$/;
// Thousands separators in the right places only: 1,000,000 or 12,500.50, not 10,00.
const GROUPED_NUMBER = /^-?\d{1,3}(,\d{3})+(\.\d+)?$/;
const WHOLE_NUMBER = /^-?\d+$/;
const RISK_BANDS: readonly string[] = ['LOW', 'MEDIUM', 'HIGH'];

const MIN_AMOUNT = 1_000;
const MAX_AMOUNT = 10_000_000;
const MIN_TERM = 1;
const MAX_TERM = 360;

export function validateQuoteForm(values: QuoteFormValues): ValidationResult {
  const errors: FieldErrors = {};

  // Checks run on the text, so decimals are counted exactly, not after float conversion.
  // Correctly grouped commas are removed; anything else with a comma is not a number,
  // so a European style "10,00" can never be read as 1000.
  const typedAmount = values.loanAmount.trim();
  const amount = GROUPED_NUMBER.test(typedAmount) ? typedAmount.replaceAll(',', '') : typedAmount;
  if (amount === '') {
    errors.loanAmount = 'Enter a loan amount';
  } else if (!NUMBER.test(amount)) {
    errors.loanAmount = 'Loan amount must be a number';
  } else if ((amount.split('.')[1] ?? '').length > 2) {
    errors.loanAmount = 'Loan amount can have at most 2 decimal places';
  } else if (Number(amount) < MIN_AMOUNT || Number(amount) > MAX_AMOUNT) {
    errors.loanAmount = 'Loan amount must be between $1,000 and $10,000,000';
  }

  const term = values.loanTermInMonths.trim();
  if (term === '') {
    errors.loanTermInMonths = 'Enter a loan term';
  } else if (!WHOLE_NUMBER.test(term)) {
    errors.loanTermInMonths = 'Loan term must be a whole number of months';
  } else if (Number(term) < MIN_TERM || Number(term) > MAX_TERM) {
    errors.loanTermInMonths = 'Loan term must be between 1 and 360 months';
  }

  if (!RISK_BANDS.includes(values.riskBand)) {
    errors.riskBand = 'Choose a risk band';
  }

  if (Object.keys(errors).length > 0) {
    return { ok: false, errors };
  }
  return {
    ok: true,
    request: {
      // At most 10 significant digits, so the number is exact when sent as JSON.
      loanAmount: Number(amount),
      loanTermInMonths: Number(term),
      riskBand: values.riskBand as RiskBand,
    },
  };
}
