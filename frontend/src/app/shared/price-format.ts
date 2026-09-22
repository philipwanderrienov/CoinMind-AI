export function formatPrice(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) {
    return '—';
  }

  const fractionDigits = value >= 1000 ? 2 : value >= 1 ? 3 : 6;

  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits
  }).format(value);
}

export function formatCompact(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) {
    return '—';
  }

  return new Intl.NumberFormat('en-US', {
    notation: 'compact',
    maximumFractionDigits: 2
  }).format(value);
}
