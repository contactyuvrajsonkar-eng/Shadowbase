const API_BASE_URL = "http://localhost:8080/api/v1";

export async function getMetrics(sandboxId) {
  const response = await fetch(
    `${API_BASE_URL}/sandboxes/${sandboxId}/metrics`
  );

  if (!response.ok) {
    throw new Error(`Metrics API failed: ${response.status}`);
  }

  return response.json();
}