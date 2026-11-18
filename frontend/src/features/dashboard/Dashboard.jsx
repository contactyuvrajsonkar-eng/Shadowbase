import "./Dashboard.css";

function Dashboard() {
  // Mock / placeholder data for Week 2
  const queryData = [
    { day: "Mon", value: 720 },
    { day: "Tue", value: 980 },
    { day: "Wed", value: 820 },
    { day: "Thu", value: 1100 },
    { day: "Fri", value: 1250 },
  ];

  const errorRateData = [
    { day: "Mon", value: 1.2 },
    { day: "Tue", value: 1.8 },
    { day: "Wed", value: 1.5 },
    { day: "Thu", value: 2.1 },
    { day: "Fri", value: 1.92 },
  ];

  return (
    <div className="app-layout">

      {/* =========================
          SIDEBAR
      ========================= */}
      <aside className="sidebar">

        <div className="brand">
          <div className="brand-icon">◉</div>
          <h2>ShadowBase</h2>
        </div>

        <nav className="sidebar-nav">

          <div className="nav-item active">
            <span className="nav-icon">⌂</span>
            <span>Dashboard</span>
          </div>

          <div className="nav-item">
            <span className="nav-icon">&lt;/&gt;</span>
            <span>Editor</span>
          </div>

          <div className="nav-item">
            <span className="nav-icon">⚙</span>
            <span>Settings</span>
          </div>

          <div className="nav-item">
            <span className="nav-icon">▥</span>
            <span>Activity</span>
          </div>

        </nav>

        <div className="system-status">
          <span className="status-dot"></span>

          <div>
            <strong>System Online</strong>
            <p>All systems operational</p>
          </div>
        </div>

      </aside>


      {/* =========================
          MAIN CONTENT
      ========================= */}
      <div className="main-content">

        {/* Header */}
        <header className="dashboard-header">
          <h1>ShadowBase Dashboard</h1>
          <p>Database Schema Migration Sandbox</p>
        </header>


        <main>

          {/* =========================
              OVERVIEW
          ========================= */}
          <section className="overview-section">

            <div className="overview-header">

              <div>
                <h2>Overview</h2>

                <p>
                  Key metrics for your ShadowBase migration activity
                </p>
              </div>

            </div>


            {/* Four Metric Boxes */}
            <div className="metrics-grid">

              {/* Queries Replayed */}
              <div className="metric-card purple-card">

                <div className="metric-icon">
                  ▶
                </div>

                <div className="metric-content">

                  <span>Queries Replayed</span>

                  <h3>1,250</h3>

                  <p className="positive">
                    +12.5% this week
                  </p>

                </div>

              </div>


              {/* Error Count */}
              <div className="metric-card green-card">

                <div className="metric-icon">
                  ⚠
                </div>

                <div className="metric-content">

                  <span>Error Count</span>

                  <h3>24</h3>

                  <p className="negative">
                    +3 this week
                  </p>

                </div>

              </div>


              {/* Error Rate */}
              <div className="metric-card blue-card">

                <div className="metric-icon">
                  %
                </div>

                <div className="metric-content">

                  <span>Error Rate</span>

                  <h3>1.92%</h3>

                  <p className="positive">
                    Within threshold
                  </p>

                </div>

              </div>


              {/* Replication Lag */}
              <div className="metric-card orange-card">

                <div className="metric-icon">
                  ◷
                </div>

                <div className="metric-content">

                  <span>Replication Lag</span>

                  <h3>120 ms</h3>

                  <p className="positive">
                    Stable
                  </p>

                </div>

              </div>

            </div>

          </section>


          {/* =========================
              CHARTS
          ========================= */}
          <section className="charts-grid">


            {/* =========================
                QUERIES REPLAYED
            ========================= */}
            <div className="chart-card">

              <div className="chart-header">

                <div>
                  <h2>Queries Replayed</h2>

                  <p>
                    Mock replay activity for the current week
                  </p>
                </div>

              </div>


              <div className="bar-chart">

                {queryData.map((item) => (

                  <div
                    className="bar-group"
                    key={item.day}
                  >

                    <span className="bar-value">
                      {item.value}
                    </span>

                    <div
                      className="query-bar"
                      style={{
                        height: `${(item.value / 1250) * 100}%`,
                      }}
                    ></div>

                    <span className="bar-day">
                      {item.day}
                    </span>

                  </div>

                ))}

              </div>

            </div>


            {/* =========================
                ERROR RATE
            ========================= */}
            <div className="chart-card">

              <div className="chart-header">

                <div>
                  <h2>Error Rate</h2>

                  <p>
                    Mock error-rate trend for the current week
                  </p>
                </div>

              </div>


              {/* Error Rate Bar Chart */}
              <div className="error-bar-chart">

                <div className="error-y-axis">
                  <span>2.5%</span>
                  <span>2.0%</span>
                  <span>1.5%</span>
                  <span>1.0%</span>
                  <span>0.5%</span>
                  <span>0%</span>
                </div>


                <div className="error-chart-area">

                  {/* Horizontal grid lines */}
                  <div className="error-grid-line grid-25"></div>
                  <div className="error-grid-line grid-20"></div>
                  <div className="error-grid-line grid-15"></div>
                  <div className="error-grid-line grid-10"></div>
                  <div className="error-grid-line grid-05"></div>
                  <div className="error-grid-line grid-00"></div>


                  {/* Bars */}
                  <div className="error-bars">

                    {errorRateData.map((item) => (

                      <div
                        className="error-bar-group"
                        key={item.day}
                      >

                        <span className="error-value">
                          {item.value}%
                        </span>

                        <div
                          className="error-bar"
                          style={{
                            height: `${(item.value / 2.5) * 100}%`,
                          }}
                        ></div>

                        <span className="error-day">
                          {item.day}
                        </span>

                      </div>

                    ))}

                  </div>

                </div>

              </div>

            </div>

          </section>


          {/* =========================
              RECENT ACTIVITY
          ========================= */}
          <section className="activity-section">

            <div className="activity-header">

              <div>
                <h2>Recent Activity</h2>

                <p>
                  Recent ShadowBase sandbox events
                </p>
              </div>

            </div>


            <div className="activity-list">

              <div className="activity-item">

                <span className="activity-dot green"></span>

                <div>
                  <strong>
                    Query replay completed
                  </strong>

                  <p>
                    1,250 queries replayed successfully
                  </p>

                  <span className="activity-time">
                    2 mins ago
                  </span>
                </div>

              </div>


              <div className="activity-item">

                <span className="activity-dot blue"></span>

                <div>
                  <strong>
                    Schema migration checked
                  </strong>

                  <p>
                    Migration validation completed
                  </p>

                  <span className="activity-time">
                    5 mins ago
                  </span>
                </div>

              </div>


              <div className="activity-item">

                <span className="activity-dot purple"></span>

                <div>
                  <strong>
                    SQL error detected
                  </strong>

                  <p>
                    24 errors recorded during replay
                  </p>

                  <span className="activity-time">
                    8 mins ago
                  </span>

                </div>

              </div>


              <div className="activity-item">

                <span className="activity-dot green"></span>

                <div>
                  <strong>
                    Replication healthy
                  </strong>

                  <p>
                    Replication lag is currently 120 ms
                  </p>

                  <span className="activity-time">
                    12 mins ago
                  </span>

                </div>

              </div>

            </div>

          </section>

        </main>

      </div>

    </div>
  );
}

export default Dashboard;