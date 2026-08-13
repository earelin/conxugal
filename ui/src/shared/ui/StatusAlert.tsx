import { Alert, type MantineColor } from '@mantine/core';
import type { ReactNode } from 'react';

interface StatusAlertProps {
  color: MantineColor;
  icon: ReactNode;
  title: string;
  children: ReactNode;
  /** The dismiss control's accessible name, in the caller's own vocabulary. */
  closeLabel: string;
  onDismiss: () => void;
}

/**
 * A report of something the reader asked for, announced politely and dismissible.
 *
 * `role="status"` rather than the assertive `role="alert"` Mantine defaults an
 * `Alert` to: this says what was asked to be told and has no business
 * interrupting a screen reader mid-sentence. `ErrorAlert` keeps the assertive
 * role, which is the case that earns it. The policy lives in one place because
 * losing it is silent — an alert announced too forcefully looks identical on
 * screen.
 */
export function StatusAlert({
  color,
  icon,
  title,
  children,
  closeLabel,
  onDismiss,
}: StatusAlertProps) {
  return (
    <Alert
      color={color}
      role="status"
      title={title}
      icon={icon}
      withCloseButton
      closeButtonLabel={closeLabel}
      onClose={onDismiss}
    >
      {children}
    </Alert>
  );
}
