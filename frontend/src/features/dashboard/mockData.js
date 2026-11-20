export const queryData = [
  { day: "Mon", value: 820 },
  { day: "Tue", value: 1040 },
  { day: "Wed", value: 960 },
  { day: "Thu", value: 1180 },
  { day: "Fri", value: 1260 },
  { day: "Sat", value: 1090 },
  { day: "Sun", value: 1310 },
];

export const errorRateData = [
  { day: "Mon", value: 1.4 },
  { day: "Tue", value: 1.6 },
  { day: "Wed", value: 1.3 },
  { day: "Thu", value: 1.8 },
  { day: "Fri", value: 1.5 },
  { day: "Sat", value: 1.2 },
  { day: "Sun", value: 1.6 },
];

export const dashboardMetrics = {
  queriesReplayed: 7660,
  errorCount: 110,
  errorRate: 1.44,
  replicationLag: 86,
};

export const recentActivity = [
  {
    title: "Query replay completed",
    description: "1,310 queries replayed successfully",
    time: "2 mins ago",
  },
  {
    title: "Schema migration checked",
    description: "Migration validation completed",
    time: "5 mins ago",
  },
  {
    title: "SQL error detected",
    description: "20 errors recorded during replay",
    time: "8 mins ago",
  },
  {
    title: "Replication healthy",
    description: "Current lag is 86 ms",
    time: "12 mins ago",
  },
];