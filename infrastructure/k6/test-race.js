import http from "k6/http";
import { check } from "k6";
import { Counter } from "k6/metrics";

// Configuration
const BASE_URL = "https://retail.yusufakcay.dev";
const JWT_TOKEN =
  "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiQURNSU4iLCJ1c2VySWQiOjEsInN1YiI6ImxldG9zaXByYSIsImlhdCI6MTc2NzcwOTA2MSwiZXhwIjoxNzY3Nzk1NDYxfQ.UCEnmvzcMj7XqqA9F8XLIkdlCtHqvQIprkwE5q3W1Dk"; // Replace with a valid JWT token for authentication
const TEST_SKU = "test";

//k6 metric
const successfulReserves = new Counter("successful_reserves");
const lockConflicts = new Counter("lock_conflicts");

export const options = {
  discardResponseBodies: true, // Critical for high RPS
  timeout: "60s",
  scenarios: {
    api_stress: {
      executor: "ramping-arrival-rate",
      // Allocate enough VUs to handle the load (k6 will reuse them)
      preAllocatedVUs: 250,
      maxVUs: 2000, // Ceiling to prevent crashing your computer

      stages: [
        // 1. WARM UP: Let JVM compile hot paths
        { target: 100, duration: "20s" },

        // 2. RAMP TO NORMAL LOAD
        { target: 200, duration: "20s" },

        // 3. RAMP TO STRESS LEVEL (2x normal)
        { target: 400, duration: "20s" },

        // 4. HOLD STRESS LEVEL
        { target: 400, duration: "20s" },

        // 5. COOLDOWN
        { target: 0, duration: "20s" },
      ],
    },
  },
  thresholds: {
    http_req_duration: ["p(95)<5000"],
    http_req_failed: ["rate<0.01"],
  },
};

const headers = {
  "Content-Type": "application/json",
  Authorization: `Bearer ${JWT_TOKEN}`,
};

export default function () {
  // Reserve 1 unit - using query parameter
  const response = http.post(
    `${BASE_URL}/inventories/${TEST_SKU}/reserve?quantity=1`,
    null,
    { headers }
  );

  check(response, {
    "status is 201 or 409": (r) => r.status === 201 || r.status === 409,
  });

  if (response.status === 201) {
    successfulReserves.add(1); // Increment by 1
  } else if (response.status === 409) {
    lockConflicts.add(1); // Increment by 1
  }
}
