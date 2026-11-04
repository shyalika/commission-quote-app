import './style.css';
import { createQuote } from './api';
import { mountQuoteApp } from './app';

mountQuoteApp(document.querySelector<HTMLElement>('#app')!, {
  createQuote,
  // The dev token selector only exists in the dev server, never in a production build.
  showDevSelector: import.meta.env.DEV,
});
