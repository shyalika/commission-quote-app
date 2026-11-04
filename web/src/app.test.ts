import {
  findByRole,
  findByText,
  fireEvent,
  getByLabelText,
  getByRole,
  queryByRole,
  waitFor,
} from '@testing-library/dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { CreateQuote } from './api';
import { mountQuoteApp } from './app';

const AMOUNT = 'Loan amount (AUD)';
const TERM = 'Loan term (months)';
const RISK_BAND = 'Risk band';

let createQuote: ReturnType<typeof vi.fn<CreateQuote>>;

beforeEach(() => {
  document.body.innerHTML = '<main id="app"></main>';
  createQuote = vi.fn<CreateQuote>().mockResolvedValue({
    kind: 'success',
    quote: { quoteId: 'q-1', commissionRate: 0.0075, totalCommission: 3750 },
  });
  mountQuoteApp(document.querySelector<HTMLElement>('#app')!, { createQuote, showDevSelector: true });
});

function fillForm({ amount = '500000', term = '360', band = 'MEDIUM' } = {}) {
  fireEvent.input(getByLabelText(document.body, AMOUNT), { target: { value: amount } });
  fireEvent.input(getByLabelText(document.body, TERM), { target: { value: term } });
  fireEvent.change(getByLabelText(document.body, RISK_BAND), { target: { value: band } });
}

function submit() {
  fireEvent.click(getByRole(document.body, 'button', { name: 'Generate quote' }));
}

/** The message linked to a field through aria-describedby. */
function describedMessage(label: string): string {
  const field = getByLabelText(document.body, label);
  const id = field.getAttribute('aria-describedby')!;
  return document.getElementById(id)?.textContent ?? '';
}

describe('validation', () => {
  it.each([
    ['empty amount', { amount: '' }, AMOUNT, 'Enter a loan amount'],
    ['negative amount', { amount: '-5000' }, AMOUNT, 'Loan amount must be between $1,000 and $10,000,000'],
    ['non numeric amount', { amount: 'abc' }, AMOUNT, 'Loan amount must be a number'],
    ['misplaced comma', { amount: '10,00' }, AMOUNT, 'Loan amount must be a number'],
    ['too many decimals', { amount: '5000.123' }, AMOUNT, 'Loan amount can have at most 2 decimal places'],
    ['term below range', { term: '0' }, TERM, 'Loan term must be between 1 and 360 months'],
    ['term above range', { term: '361' }, TERM, 'Loan term must be between 1 and 360 months'],
    ['term not whole', { term: '12.5' }, TERM, 'Loan term must be a whole number of months'],
  ])('%s shows a field message and blocks submit', (_case, values, label, message) => {
    fillForm(values);

    submit();

    expect(describedMessage(label)).toBe(message);
    expect(getByLabelText(document.body, label).getAttribute('aria-invalid')).toBe('true');
    expect(createQuote).not.toHaveBeenCalled();
  });

  it('valid input submits once', () => {
    fillForm();

    submit();
    submit();

    expect(createQuote).toHaveBeenCalledTimes(1);
    expect(createQuote).toHaveBeenCalledWith(
      { loanAmount: 500000, loanTermInMonths: 360, riskBand: 'MEDIUM' },
      expect.objectContaining({ token: 'dev-staff' }),
    );
  });
});

describe('thousands separators', () => {
  it.each([
    ['1,000,000', 1000000],
    ['12,500.50', 12500.5],
  ])('amount %s is accepted and sent without commas', (amount, expected) => {
    fillForm({ amount });

    submit();

    expect(createQuote).toHaveBeenCalledWith(
      expect.objectContaining({ loanAmount: expected }),
      expect.anything(),
    );
  });
});

describe('request outcome', () => {
  const MESSAGE = "Sorry, we couldn't get a quote right now.";

  it('the button is disabled and loading shows while the request is pending', () => {
    createQuote.mockReturnValue(new Promise(() => {}));
    fillForm();

    submit();

    expect(getByRole(document.body, 'button', { name: 'Generate quote' })).toHaveProperty('disabled', true);
    expect(getByRole(document.body, 'status').textContent).toBe('Generating quote…');
  });

  it('a successful quote is displayed', async () => {
    fillForm();

    submit();

    expect(await findByText(document.body, 'q-1')).toBeTruthy();
    expect(await findByText(document.body, '0.75%')).toBeTruthy();
    expect(await findByText(document.body, '$3,750.00')).toBeTruthy();
    expect(getByRole(document.body, 'button', { name: 'Generate quote' })).toHaveProperty('disabled', false);
  });

  it.each([true, false])(
    'an error shows the message and reference, with Try again only when retryable (retryable: %s)',
    async (retryable) => {
      createQuote.mockResolvedValue({ kind: 'error', message: MESSAGE, retryable, correlationId: 'ref-123' });
      fillForm();

      submit();

      expect(await findByText(document.body, MESSAGE)).toBeTruthy();
      expect(await findByText(document.body, 'Reference: ref-123')).toBeTruthy();
      expect(queryByRole(document.body, 'button', { name: 'Try again' }) !== null).toBe(retryable);
    },
  );

  it('Try again resends with the same Idempotency-Key', async () => {
    createQuote
      .mockResolvedValueOnce({ kind: 'error', message: MESSAGE, retryable: true, correlationId: 'ref-123' })
      .mockResolvedValueOnce({
        kind: 'success',
        quote: { quoteId: 'q-1', commissionRate: 0.0075, totalCommission: 3750 },
      });
    fillForm();
    submit();

    fireEvent.click(await findByRole(document.body, 'button', { name: 'Try again' }));

    await waitFor(() => expect(createQuote).toHaveBeenCalledTimes(2));
    const [firstRequest, firstOptions] = createQuote.mock.calls[0];
    const [retryRequest, retryOptions] = createQuote.mock.calls[1];
    expect(retryRequest).toEqual(firstRequest);
    expect(firstOptions.idempotencyKey).toBeTruthy();
    expect(retryOptions.idempotencyKey).toBe(firstOptions.idempotencyKey);
  });
});
