/** Timezone-safe date formatting utilities for API communication. */

/** Formats a Date to 'YYYY-MM-DD' using local date parts (avoids UTC shift from toISOString). */
export function formatDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/** Combines a Date and a time string ('HH:mm') into ISO datetime 'YYYY-MM-DDTHH:mm:00'. */
export function combineDatetime(date: Date, time: string): string {
  return `${formatDate(date)}T${time}:00`;
}

/** Extracts 'HH:mm' from an ISO datetime string. */
export function parseTime(iso: string): string {
  return iso.substring(11, 16);
}
