/**
 * Vitest setup -- runs once before each test file. Wires Testing Library's custom matchers
 * (toBeInTheDocument, toHaveAttribute, etc.) into Vitest's expect.
 */
import '@testing-library/jest-dom';
