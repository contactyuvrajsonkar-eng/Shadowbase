import "./Dashboard.css";

function Dashboard() {
  return (
    <div className="dashboard">
      <header className="dashboard-header">
        <h1>ShadowBase Dashboard</h1>
        <p>Database Schema Migration Sandbox</p>
      </header>

      <main>
        <section className="dashboard-section">
          <h2>Overview</h2>

          <div className="metrics-grid">
            <div className="metric-card">
              <h3>Queries Replayed</h3>
              <p className="metric-value">1,250</p>
            </div>

            <div className="metric-card">
              <h3>Error Count</h3>
              <p className="metric-value">24</p>
            </div>

            <div className="metric-card">
              <h3>Error Rate</h3>
              <p className="metric-value">1.92%</p>
            </div>

            <div className="metric-card">
              <h3>Replication Lag</h3>
              <p className="metric-value">120 ms</p>
            </div>
          </div>
        </section>

        <section className="dashboard-section">
          <h2>Recent Activity</h2>

          <div className="activity-card">
            <div className="activity-item">
              <div>
                <strong>Query replay completed</strong>
                <p>1,250 queries replayed successfully.</p>
              </div>
              <span>2 min ago</span>
            </div>

            <div className="activity-item">
              <div>
                <strong>Schema migration checked</strong>
                <p>Migration validation completed.</p>
              </div>
              <span>8 min ago</span>
            </div>

            <div className="activity-item">
              <div>
                <strong>SQL error detected</strong>
                <p>24 errors recorded during replay.</p>
              </div>
              <span>15 min ago</span>
            </div>
          </div>
        </section>
      </main>
    </div>
  );
}

export default Dashboard;