import http from "k6/http";
import { check } from "k6";

// Configuration
const BASE_URL = "https://retail.yusufakcay.dev";
const JWT_TOKEN =
  "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiQURNSU4iLCJ1c2VySWQiOjEsInN1YiI6ImxldG9zaXByYSIsImlhdCI6MTc2NzcwOTA2MSwiZXhwIjoxNzY3Nzk1NDYxfQ.UCEnmvzcMj7XqqA9F8XLIkdlCtHqvQIprkwE5q3W1Dk"; // Replace with a valid JWT token for authentication
const TEST_SKU = "test";

export const options = {
  discardResponseBodies: true,
  scenarios: {
    api_stress: {
      executor: "ramping-arrival-rate",

      // Very conservative - find baseline capacity
      preAllocatedVUs: 10,
      maxVUs: 30,

      stages: [
        // 1. START LOW: Establish baseline
        { target: 10, duration: "20s" },

        // 2. SLOW RAMP: Find capacity gradually
        { target: 20, duration: "30s" },
        { target: 30, duration: "30s" },

        // 3. SUSTAIN: Hold for observation
        { target: 30, duration: "20s" },

        // 4. COOLDOWN
        { target: 0, duration: "10s" },
      ],
    },
  },
  thresholds: {
    http_req_duration: ["p(95)<2000"],
    http_req_failed: ["rate<0.30"], // 30% failure threshold
    "checks{check:status is 201 or 409}": ["rate>0.70"], // 70% success needed
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
    "status is 201 or 429": (r) => r.status === 201 || r.status === 429,
  });

  if (response.status === 201) {
    console.log("✅ Reserved 1 unit");
  } else if (response.status === 429) {
    console.log("⏱️  Lock conflict (expected under high load)");
  } else {
    console.error(`❌ Status: ${response.status} - ${response.body}`);
  }
}

export function teardown() {
  console.log("\n✅ Race condition test complete!\n");
}
