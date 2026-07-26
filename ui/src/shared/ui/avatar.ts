export function initialsOf(email: string): string {
  return email.split('@')[0].slice(0, 2).toUpperCase();
}
