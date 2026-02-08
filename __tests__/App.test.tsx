/**
 * @format
 */

import React from 'react';
import ReactTestRenderer from 'react-test-renderer';
import App from '../App';

jest.mock('react-native-share-menu', () => ({
  getInitialShare: jest.fn(),
  addNewShareListener: jest.fn(() => ({
    remove: jest.fn(),
  })),
}));

jest.mock('react-native-html-to-pdf', () => ({
  convert: jest.fn(),
}));

jest.mock('react-native-print', () => ({
  print: jest.fn(),
}));

test('renders correctly', async () => {
  await ReactTestRenderer.act(() => {
    ReactTestRenderer.create(<App />);
  });
});
