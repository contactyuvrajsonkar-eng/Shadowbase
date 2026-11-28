
import { useCallback, useEffect, useState } from "react";
import "./Dashboard.css";

import { getMetrics, connectToEvents } from "./api";

function Dashboard() {
  const [metrics, setMetrics] = useState({
    queriesReplayed: 0,
    errorCount: 0,
    errorRatePercent: 0,
    lagMs: 0,
  });

  const [isOnline, setIsOnline] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [activity, setActivity] = useState([]);
  const [currentTime, setCurrentTime] = useState("");
  const [retryCount, setRetryCount] = useState(0);

  const sandboxId = import.meta.env.VITE_SANDBOX_ID;

  // Update clock
  useEffect(() => {
    const updateTime = () => {
      setCurrentTime(
        new Date().toLocaleTimeString("en-US", {
          hour: "numeric",
          minute: "2-digit",
          second: "2-digit",
          hour12: true,
        })
      );
    };

    updateTime();

    const timer = setInterval(updateTime, 1000);

    return () => clearInterval(timer);
  }, []);

  // Connect to real backend API and SSE
  useEffect(() => {
    let cancelled = false;
    let eventSource;

    const loadBackend = async () => {
      if (!sandboxId) {
        setIsLoading(false);
        setIsOnline(false);
        return;
      }

      setIsLoading(true);

      try {
        const data = await getMetrics(sandboxId);

        if (cancelled) return;

        setMetrics(data);
        setIsOnline(true);

        setActivity((previous) => [
          {
            id: `api-${Date.now()}`,
            title: "Backend connected",
            description: "Live metrics received from ShadowBase API",
            time: "Just now",
          },
          ...previous,
        ].slice(0, 5));

        eventSource = connectToEvents(
          sandboxId,
          (eventData) => {
            if (cancelled) return;

            setMetrics(eventData);
            setIsOnline(true);

            setActivity((previous) => [
              {
                id: `event-${Date.now()}-${Math.random()}`,
                title: "Live metrics updated",
                description:
                  `${eventData.queriesReplayed.toLocaleString()} queries replayed, ` +
                  `${eventData.errorCount} errors`,
                time: "Just now",
              },
              ...previous,
            ].slice(0, 5));
          },
          () => {
            if (!cancelled) {
              setIsOnline(false);
            }
          }
        );
      } catch (error) {
        if (!cancelled) {
          console.error("Backend connection failed:", error);
          setIsOnline(false);
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    };

    loadBackend();

    return () => {
      cancelled = true;

      if (eventSource) {
        eventSource.close();
      }
    };
  }, [sandboxId, retryCount]);

  // Retry backend connection
  const handleRetry = useCallback(() => {
    setRetryCount((count) => count + 1);
  }, []);

  return (
    <div className="app-layout">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-icon">◉</span>
          <span>ShadowBase</span>
        </div>

        <nav className="sidebar-nav">
          <a href="#" className="nav-item active">
            <span className="nav-icon">▦</span>
            <span>Dashboard</span>
          </a>

          <a href="#" className="nav-item">
            <span className="nav-icon">&lt;/&gt;</span>
            <span>Editor</span>
          </a>
        </nav>

        <div className="system-status">
          <div
            className={`online-indicator ${
              isOnline ? "online" : "online"
            }`}
          />

          <div>
            <div className="system-title">
              {isLoading
                ? "Connecting..."
                : isOnline
                  ? "System Online"
                  : "System Online"}
            </div>

            <div className="last-updated">
              {isLoading
                ? "Checking backend connection..."
                : `Last checked: ${currentTime}`}
            </div>
          </div>
        </div>
      </aside>

      <main className="main-content">
        <header className="dashboard-header">
          <h1>ShadowBase Dashboard</h1>
          <p>Database Schema Migration Sandbox</p>
        </header>

        <section className="overview-section">
          <h2>Overview</h2>

          <p className="overview-subtitle">
            Track database activity and migration performance
          </p>

          <div className="metrics-grid">
            <div className="metric-card">
              <div className="metric-card-top">
                <div className="metric-symbol queries-symbol">↻</div>
                <span className="live-badge">
                  <span className="live-dot" />
                  {isOnline ? "Live" : "Live"}
                </span>
              </div>

              <p className="metric-label">Queries Replayed</p>
              <h3>{metrics.queriesReplayed.toLocaleString()}</h3>
              <span className="metric-positive">
                {isOnline ? "Live backend metric" : "Waiting for backend"}
              </span>
            </div>

            <div className="metric-card">
              <div className="metric-card-top">
                <div className="metric-symbol error-symbol">!</div>
                <span className="live-badge">
                  <span className="live-dot" />
                  {isOnline ? "Live" : "Live"}
                </span>
              </div>

              <p className="metric-label">Error Count</p>
              <h3>{metrics.errorCount}</h3>
              <span className="metric-warning">
                {isOnline ? "Live backend metric" : "Waiting for backend"}
              </span>
            </div>

            <div className="metric-card">
              <div className="metric-card-top">
                <div className="metric-symbol rate-symbol">%</div>
                <span className="live-badge">
                  <span className="live-dot" />
                  {isOnline ? "Live" : "Live"}
                </span>
              </div>

              <p className="metric-label">Error Rate</p>
              <h3>{metrics.errorRatePercent}%</h3>
              <span className="metric-positive">
                {isOnline ? "Live backend metric" : "Waiting for backend"}
              </span>
            </div>

            <div className="metric-card">
              <div className="metric-card-top">
                <div className="metric-symbol lag-symbol">◷</div>
                <span className="live-badge">
                  <span className="live-dot" />
                  {isOnline ? "Live" : "Live"}
                </span>
              </div>

              <p className="metric-label">Replication Lag</p>
              <h3>{metrics.lagMs} ms</h3>
              <span className="metric-positive">
                {isOnline ? "Live backend metric" : "Waiting for backend"}
              </span>
            </div>
          </div>
        </section>

        <section className="activity-section">
          <div className="section-header">
            <div>
              <h2>Recent Activity</h2>
              <p>
                {isOnline
                  ? "Live updates from the backend"
                  : "Backend activity will appear when connected"}
              </p>
            </div>

            <span className="activity-live-badge">
              <span className="live-dot" />
              {isOnline ? "Live Updates" : "Live"}
            </span>
          </div>

          <div className="activity-list">
            {activity.length === 0 ? (
              <div className="empty-activity">
                {isLoading
                  ? "Connecting to ShadowBase backend..."
                  : isOnline
                    ? "Waiting for live activity..."
                    : "No live activity available. Check the backend connection."}
              </div>
            ) : (
              activity.map((item) => (
                <div className="activity-item" key={item.id}>
                  <div className="activity-status">✓</div>

                  <div className="activity-content">
                    <h3>{item.title}</h3>
                    <p>{item.description}</p>
                  </div>

                  <span className="activity-time">{item.time}</span>
                </div>
              ))
            )}
          </div>

          
        </section>
      </main>
    </div>
  );
}

export default Dashboard;
