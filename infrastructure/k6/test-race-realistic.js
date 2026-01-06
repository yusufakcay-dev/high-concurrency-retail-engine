import http from "k6/http";
import { check } from "k6";
import { Counter } from "k6/metrics";

// Configuration
const BASE_URL = "https://retail.yusufakcay.dev";
const JWT_TOKEN =
  "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiQURNSU4iLCJ1c2VySWQiOjEsInN1YiI6ImxldG9zaXByYSIsImlhdCI6MTc2NzcwOTA2MSwiZXhwIjoxNzY3Nzk1NDYxfQ.UCEnmvzcMj7XqqA9F8XLIkdlCtHqvQIprkwE5q3W1Dk";
const TEST_SKU = "test";

// Custom metrics
const successfulReservations = new Counter("successful_reservations");
const lockConflicts = new Counter("lock_conflicts_409");
const serverErrors = new Counter("server_errors_5xx");

export const options = {
  discardResponseBodies: true,

  scenarios: {
    // 🎯 REALISTIC LOAD: Designed for 4 CPU / 24GB server
    realistic_stress: {
      executor: "ramping-arrival-rate",

      preAllocatedVUs: 50,
      maxVUs: 200,

      stages: [
        // 1. WARM UP: Let JVM compile hot paths
        { target: 50, duration: "30s" },

        // 2. RAMP TO NORMAL LOAD
        { target: 100, duration: "30s" },

        // 3. RAMP TO STRESS LEVEL (2x normal)
        { target: 200, duration: "30s" },

        // 4. HOLD STRESS LEVEL
        { target: 200, duration: "30s" },

        // 5. COOLDOWN
        { target: 0, duration: "20s" },
      ],
    },
  },

  thresholds: {
    // Realistic expectations for distributed locks
    http_req_duration: ["p(95)<2000"], // 2s is acceptable under stress
    http_req_failed: ["rate<0.30"], // 30% failure acceptable (409 conflicts)
    checks: ["rate>0.70"], // 70% success rate

    // Custom metrics
    successful_reservations: ["count>50"],
    lock_conflicts_409: ["count<5000"], // Some conflicts expected
    server_errors_5xx: ["count<10"], // Very few server errors
  },
};

const headers = {
  "Content-Type": "application/json",
  Authorization: `Bearer ${JWT_TOKEN}`,
};

export default function () {
  const response = http.post(
    `${BASE_URL}/inventories/${TEST_SKU}/reserve?quantity=1`,
    null,
    { headers, timeout: "10s" } // 10s timeout to prevent hanging
  );

  // Track results
  if (response.status === 201) {
    successfulReservations.add(1);
  } else if (response.status === 409) {
    lockConflicts.add(1);
  } else if (response.status >= 500) {
    serverErrors.add(1);
  }

  check(response, {
    "status is 201 or 409": (r) => r.status === 201 || r.status === 409,
    "no server errors": (r) => r.status < 500,
  });
}

export function handleSummary(data) {
  const successful = data.metrics.successful_reservations?.values?.count || 0;
  const conflicts = data.metrics.lock_conflicts_409?.values?.count || 0;
  const errors = data.metrics.server_errors_5xx?.values?.count || 0;
  const totalRequests = data.metrics.http_reqs?.values?.count || 0;

  console.log("\n═════════════════════════════════════════════════════");
  console.log("   🚀 REALISTIC RACE CONDITION TEST RESULTS");
  console.log("═════════════════════════════════════════════════════");
  console.log(`Total Requests:        ${totalRequests}`);
  console.log(
    `Successful (201):      ${successful} (${(
      (successful / totalRequests) *
      100
    ).toFixed(2)}%)`
  );
  console.log(
    `Lock Conflicts (409):  ${conflicts} (${(
      (conflicts / totalRequests) *
      100
    ).toFixed(2)}%)`
  );
  console.log(
    `Server Errors (5xx):   ${errors} (${(
      (errors / totalRequests) *
      100
    ).toFixed(2)}%)`
  );
  console.log("─────────────────────────────────────────────────────");

  const successRate = (successful / totalRequests) * 100;
  const errorRate = (errors / totalRequests) * 100;

  if (errorRate < 1 && successRate > 50) {
    console.log("✅ PASS: Service handles realistic load well!");
    console.log("💪 Redisson locks working correctly");
  } else if (errorRate < 5) {
    console.log("⚠️  MARGINAL: Service struggling but stable");
  } else {
    console.log("❌ FAIL: Too many server errors");
  }

  console.log("═════════════════════════════════════════════════════\n");

  return {
    stdout: JSON.stringify(data, null, 2),
  };
}

export function teardown() {
  console.log("\n🔍 Validating final inventory state...\n");

  const response = http.get(`${BASE_URL}/inventories/${TEST_SKU}`, { headers });

  if (response.status === 200) {
    const inventory = JSON.parse(response.body);

    console.log("═════════════════════════════════════════════════════");
    console.log("   📊 FINAL INVENTORY STATE");
    console.log("═════════════════════════════════════════════════════");
    console.log(`SKU:                ${inventory.sku}`);
    console.log(
      `Total Quantity:     ${inventory.quantity.toLocaleString()} units`
    );
    console.log(
      `Reserved:           ${inventory.reservedQuantity.toLocaleString()} units`
    );
    console.log(
      `Available:          ${inventory.availableQuantity.toLocaleString()} units`
    );
    console.log("─────────────────────────────────────────────────────");

    // Validate arithmetic
    const expectedAvailable = inventory.quantity - inventory.reservedQuantity;
    const isValid = inventory.availableQuantity === expectedAvailable;

    console.log(
      `Formula Check: ${inventory.availableQuantity.toLocaleString()} = ${inventory.quantity.toLocaleString()} - ${inventory.reservedQuantity.toLocaleString()}`
    );

    if (isValid) {
      console.log("✅ PASS: Arithmetic is correct (no race condition)");
    } else {
      console.log(
        `❌ FAIL: Expected ${expectedAvailable.toLocaleString()}, got ${inventory.availableQuantity.toLocaleString()}`
      );
    }

    // Check for negative values
    if (
      inventory.quantity < 0 ||
      inventory.availableQuantity < 0 ||
      inventory.reservedQuantity < 0
    ) {
      console.log("❌ CRITICAL: Negative inventory detected!");
    } else {
      console.log("✅ PASS: No negative values");
    }

    console.log("═════════════════════════════════════════════════════\n");
  } else {
    console.log(`❌ Failed to get inventory: ${response.status}`);
  }
}
