const sensitivePatterns = [
  "database_url",
  "supabase",
  "razorpay",
  "secret",
  "api key",
  "traceback",
  "psycopg",
  "sqlalchemy",
  "internal server error",
  "not found",
  "token"
];

export function safeApiErrorMessage(message: unknown, fallback = "Request could not be completed. Please try again.") {
  if (typeof message !== "string" || !message.trim()) {
    return fallback;
  }

  const normalized = message.toLowerCase();
  if (sensitivePatterns.some((pattern) => normalized.includes(pattern))) {
    return fallback;
  }

  return message;
}
