import type { RouteDraftValidationReport, RouteExecutorDocument } from '@/types/routeExecutor'

export function hasRouteDraftErrors(report: Pick<RouteDraftValidationReport, 'issues'>): boolean {
  return report.issues.some((issue) => issue.severity === 'ERROR')
}

export async function validateThenSaveRouteDraft(
  validate: () => Promise<RouteDraftValidationReport>,
  save: (document: RouteExecutorDocument) => Promise<void>,
): Promise<RouteDraftValidationReport> {
  const report = await validate()
  if (!hasRouteDraftErrors(report)) {
    await save(report.normalizedExecutorJson)
  }
  return report
}
