import { NotificationSeverity } from './notification.service';

export function severityColor(severity: NotificationSeverity): string {
  const map: Record<NotificationSeverity, string> = {
    CRITICAL: '#EF4444',
    WARNING: '#F97316',
    INFO: '#0099B8',
  };
  return map[severity];
}

export function formatNotificationDate(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}
