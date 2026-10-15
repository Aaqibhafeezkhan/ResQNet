import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';

import { App } from './app/App';
import './styles/index.css';

/**
 * Entry point.
 *
 * <p>StrictMode is on deliberately. It double-renders in development and will
 * surface effect cleanup bugs here that would otherwise show up as a duplicate
 * WebSocket connection on a handset.
 */
const container = document.getElementById('root');
if (!container) {
  throw new Error('Root container missing from index.html');
}

createRoot(container).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
);
