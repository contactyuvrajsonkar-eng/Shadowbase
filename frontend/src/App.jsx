
import { useState } from "react";
import Dashboard from "./features/dashboard/Dashboard";

function App() {
  const [activePage, setActivePage] = useState("dashboard");

  return (
    <Dashboard
      activePage={activePage}
      onNavigate={setActivePage}
    />
  );
}

export default App;
