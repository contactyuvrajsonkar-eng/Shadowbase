import "./Dashboard.css";

import {
  queryData,
  errorRateData,
  dashboardMetrics,
  recentActivity,
} from "./mockData";

function Dashboard() {
  const maxQueries = Math.max(
    ...queryData.map((item) => item.value)
  );

  const maxErrorRate = Math.max(
    ...errorRateData.map((item) => item.value)
  );

  return (
    <div className="app-layout">
      {/* Sidebar */}
      <aside className="sidebar">
        <div className="brand">ShadowBase</div>

        <nav className="sidebar-nav">
          <a href="#" className="nav-item active">
            Dashboard
          </a>

          <a href="#" className="nav-item">
            Editor
          </a>

          <a href="#" className="nav-item">
            Settings
          </a>

          <a href="#" className="nav-item">
            Activity
          </a>
        </nav>

        <div className="system-status">
          <span className="status-dot"></span>
          <span>System Online</span>
        </div>
      </aside>

      {/* Main Content */}
      <main className="main-content">
        {/* Header */}
        <header className="dashboard-header">
          <h1>ShadowBase Dashboard</h1>
          <p>Database Schema Migration Sandbox</p>
        </header>

        {/* Overview */}
        <section className="overview-section">
          <h2>Overview</h2>
          
          <p className="overview-subtitle">
           Track database activity and migration performance
        </p>

          <div className="metrics-grid">
            <div className="metric-card">
              <div className="metric-icon">↻</div>

              <div>
                <p className="metric-label">
                  Queries Replayed
                </p>

                <h3>
                  {dashboardMetrics.queriesReplayed.toLocaleString()}
                </h3>

                <span className="metric-positive">
                  +12.5% this week
                </span>
              </div>
            </div>

            <div className="metric-card">
              <div className="metric-icon">!</div>

              <div>
                <p className="metric-label">
                  Error Count
                </p>

                <h3>{dashboardMetrics.errorCount}</h3>

                <span className="metric-warning">
                  +3 this week
                </span>
              </div>
            </div>

            <div className="metric-card">
              <div className="metric-icon">%</div>

              <div>
                <p className="metric-label">
                  Error Rate
                </p>

                <h3>{dashboardMetrics.errorRate}%</h3>

                <span className="metric-positive">
                  Within threshold
                </span>
              </div>
            </div>

            <div className="metric-card">
              <div className="metric-icon">◷</div>

              <div>
                <p className="metric-label">
                  Replication Lag
                </p>

                <h3>
                  {dashboardMetrics.replicationLag} ms
                </h3>

                <span className="metric-positive">
                  Stable
                </span>
              </div>
            </div>
          </div>
        </section>

        {/* Charts */}
        <section className="charts-section">
          {/* Queries Replayed */}
          <div className="chart-card">
            <div className="chart-header">
              <div>
                <h2>Queries Replayed</h2>
                <p>Queries replayed during the week</p>
              </div>
            </div>

            <div className="bar-chart">
              {queryData.map((item) => {
                const barHeight =
                  (item.value / maxQueries) * 100;

                return (
                  <div
                    className="bar-column"
                    key={item.day}
                  >
                    <div className="bar-value">
                      {item.value.toLocaleString()}
                    </div>

                    <div className="bar-wrapper">
                      <div
                        className="bar"
                        style={{
                          height: `${barHeight}%`,
                        }}
                      ></div>
                    </div>

                    <span className="bar-label">
                      {item.day}
                    </span>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Error Rate */}
          <div className="chart-card">
            <div className="chart-header">
              <div>
                <h2>Error Rate</h2>
                <p>Error rate during query replay</p>
              </div>
            </div>

            <div className="bar-chart">
              {errorRateData.map((item) => {
                const barHeight =
                  (item.value / maxErrorRate) * 100;

                return (
                  <div
                    className="bar-column"
                    key={item.day}
                  >
                    <div className="bar-value">
                      {item.value}%
                    </div>

                    <div className="bar-wrapper">
                      <div
                        className="bar error-bar"
                        style={{
                          height: `${barHeight}%`,
                        }}
                      ></div>
                    </div>

                    <span className="bar-label">
                      {item.day}
                    </span>
                  </div>
                );
              })}
            </div>
          </div>
        </section>

        {/* Recent Activity */}
        <section className="activity-section">
          <div className="section-header">
            <h2>Recent Activity</h2>
          </div>

          <div className="activity-list">
            {recentActivity.map((activity, index) => (
              <div
                className="activity-item"
                key={index}
              >
                <div className="activity-content">
                  <h3>{activity.title}</h3>

                  <p>{activity.description}</p>
                </div>

                <span className="activity-time">
                  {activity.time}
                </span>
              </div>
            ))}
          </div>
        </section>
      </main>
    </div>
  );
}

export default Dashboard;