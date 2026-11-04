// Rendering: builds the form and updates what the user sees. No fetching and
// no state; app.ts decides what to show.

import type { Quote } from './api';
import type { FieldErrors, QuoteField } from './validation';

export interface QuoteView {
  form: HTMLFormElement;
  fields: Record<QuoteField, HTMLInputElement | HTMLSelectElement>;
  /** Dev only "Signed in as" selector; absent in production builds. */
  signedInAs?: HTMLSelectElement;
  submit: HTMLButtonElement;
  status: HTMLElement;
  error: HTMLElement;
  errorMessage: HTMLElement;
  errorReference: HTMLElement;
  tryAgain: HTMLButtonElement;
}

const FIELDS: readonly QuoteField[] = ['loanAmount', 'loanTermInMonths', 'riskBand'];

const percent = new Intl.NumberFormat('en-AU', { style: 'percent', minimumFractionDigits: 2, maximumFractionDigits: 4 });
const aud = new Intl.NumberFormat('en-AU', { style: 'currency', currency: 'AUD' });

// Static markup only; values from the user or the server are set with textContent.
// Only used behind import.meta.env.DEV, so production builds drop it entirely.
const DEV_SELECTOR = `
  <div class="field dev-only">
    <label for="signed-in-as">Signed in as (dev only)</label>
    <select id="signed-in-as">
      <option value="dev-staff">Staff (can create quotes)</option>
      <option value="dev-readonly">Read only</option>
    </select>
  </div>`;

const fieldError = (field: QuoteField) => `<p id="${field}-error" class="field-error" hidden></p>`;

export function renderForm(root: HTMLElement, { showDevSelector }: { showDevSelector: boolean }): QuoteView {
  root.innerHTML = `
    <h1>Commission quote</h1>
    <form novalidate>
      ${import.meta.env.DEV && showDevSelector ? DEV_SELECTOR : ''}
      <div class="field">
        <label for="loanAmount">Loan amount (AUD)</label>
        <input id="loanAmount" name="loanAmount" type="text" inputmode="decimal" autocomplete="off"
               required aria-describedby="loanAmount-error">
        ${fieldError('loanAmount')}
      </div>
      <div class="field">
        <label for="loanTermInMonths">Loan term (months)</label>
        <input id="loanTermInMonths" name="loanTermInMonths" type="text" inputmode="numeric" autocomplete="off"
               required aria-describedby="loanTermInMonths-error">
        ${fieldError('loanTermInMonths')}
      </div>
      <div class="field">
        <label for="riskBand">Risk band</label>
        <select id="riskBand" name="riskBand" required aria-describedby="riskBand-error">
          <option value="">Choose a risk band</option>
          <option value="LOW">Low</option>
          <option value="MEDIUM">Medium</option>
          <option value="HIGH">High</option>
        </select>
        ${fieldError('riskBand')}
      </div>
      <button type="submit">Generate quote</button>
    </form>
    <div class="status" role="status" aria-live="polite"></div>
    <div class="error" role="alert" hidden>
      <p class="error-message"></p>
      <p class="error-reference"></p>
      <button type="button" class="try-again" hidden>Try again</button>
    </div>`;

  const find = <T extends Element>(selector: string) => root.querySelector<T>(selector)!;
  return {
    form: find('form'),
    fields: {
      loanAmount: find('#loanAmount'),
      loanTermInMonths: find('#loanTermInMonths'),
      riskBand: find('#riskBand'),
    },
    signedInAs: root.querySelector<HTMLSelectElement>('#signed-in-as') ?? undefined,
    submit: find('button[type="submit"]'),
    status: find('.status'),
    error: find('.error'),
    errorMessage: find('.error-message'),
    errorReference: find('.error-reference'),
    tryAgain: find('.try-again'),
  };
}

/** Shows each message under its field and marks the field invalid; clears the others. */
export function showFieldErrors(view: QuoteView, errors: FieldErrors): void {
  for (const field of FIELDS) {
    const message = errors[field];
    const element = view.fields[field];
    const errorText = document.getElementById(`${field}-error`)!;
    errorText.textContent = message ?? '';
    errorText.hidden = !message;
    if (message) {
      element.setAttribute('aria-invalid', 'true');
    } else {
      element.removeAttribute('aria-invalid');
    }
  }
}

export function focusFirstInvalid(view: QuoteView, errors: FieldErrors): void {
  const first = FIELDS.find((field) => errors[field]);
  if (first) {
    view.fields[first].focus();
  }
}

export function showLoading(view: QuoteView): void {
  view.submit.disabled = true;
  view.tryAgain.disabled = true;
  hideError(view);
  view.status.textContent = 'Generating quote…';
}

export function showStillWorking(view: QuoteView): void {
  view.status.textContent = 'Still working…';
}

export function showResult(view: QuoteView, quote: Quote): void {
  enableActions(view);
  hideError(view);
  const list = document.createElement('dl');
  list.className = 'result';
  addRow(list, 'Quote ID', quote.quoteId);
  addRow(list, 'Commission rate', percent.format(quote.commissionRate));
  addRow(list, 'Total commission', aud.format(quote.totalCommission));
  view.status.replaceChildren(list);
}

export function showError(
  view: QuoteView,
  error: { message: string; retryable: boolean; correlationId?: string },
): void {
  enableActions(view);
  view.status.textContent = '';
  view.errorMessage.textContent = error.message;
  view.errorReference.textContent = error.correlationId ? `Reference: ${error.correlationId}` : '';
  view.errorReference.hidden = !error.correlationId;
  view.tryAgain.hidden = !error.retryable;
  view.error.hidden = false;
}

export function hideTryAgain(view: QuoteView): void {
  view.tryAgain.hidden = true;
}

/** Removes a displayed quote; a loading message, if any, stays. */
export function clearResult(view: QuoteView): void {
  view.status.querySelector('.result')?.remove();
}

function hideError(view: QuoteView): void {
  view.error.hidden = true;
}

function enableActions(view: QuoteView): void {
  view.submit.disabled = false;
  view.tryAgain.disabled = false;
}

function addRow(list: HTMLElement, label: string, value: string): void {
  const term = document.createElement('dt');
  term.textContent = label;
  const detail = document.createElement('dd');
  detail.textContent = value;
  list.append(term, detail);
}
