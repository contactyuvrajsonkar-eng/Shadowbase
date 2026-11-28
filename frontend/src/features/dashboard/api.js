const API_BASE_URL = "/api/v1";

export async function getMetrics(sandboxId) {
  const response = await fetch(
    `${API_BASE_URL}/sandboxes/${sandboxId}/metrics`
  );

  if (!response.ok) {
    throw new Error(`Metrics API failed: ${response.status}`);
  }

  return response.json();
}

export function connectToEvents(sandboxId, onMetrics, onError) {
  const eventSource = new EventSource(
    `${API_BASE_URL}/sandboxes/${sandboxId}/events`
  );

  eventSource.addEventListener("metrics", (event) => {
    try {
      const data = JSON.parse(event.data);
      onMetrics(data);
    } catch (error) {
      onError?.(new Error("Invalid metrics event received."));
    }
  });

  eventSource.onerror = () => {
    onError?.(new Error("Live activity connection lost."));
  };

  return eventSource;
}