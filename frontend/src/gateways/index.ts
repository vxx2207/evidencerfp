import type { AnalysisGateway } from '../types';
import { ApiAnalysisGateway } from './ApiAnalysisGateway';
import { DemoAnalysisGateway } from './DemoAnalysisGateway';

export function createAnalysisGateway(): AnalysisGateway {
  return import.meta.env.VITE_APP_MODE === 'api' ? new ApiAnalysisGateway() : new DemoAnalysisGateway();
}

export { ApiAnalysisGateway, DemoAnalysisGateway };
