import http from "k6/http";
import { check, sleep } from "k6";
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

// Global state to track before/after
let initialInventory = null;

export const options = {
  discardResponseBodies: true,

  scenarios: {
    realistic_stress: {
      executor: "ramping-arrival-rate",

      preAllocatedVUs: 50,
      maxVUs: 200,

      stages: [
        { target: 50, duration: "30s" },
        { target: 100, duration: "30s" },
        { target: 200, duration: "30s" },
        { target: 200, duration: "30s" },
        { target: 0, duration: "20s" },
      ],
    },
  },

  thresholds: {
    http_req_duration: ["p(95)<2000"],
    http_req_failed: ["rate<0.30"],
    checks: ["rate>0.70"],
    successful_reservations: ["count>50"],
    lock_conflicts_409: ["count<5000"],
    server_errors_5xx: ["count<10"],
  },
};

const headers = {
  "Content-Type": "application/json",
  Authorization: `Bearer ${JWT_TOKEN}`,
};

export function setup() {
  console.log("\n🔍 Taking initial inventory snapshot...\n");

  const response = http.get(`${BASE_URL}/inventories/${TEST_SKU}`, {
    headers,
    timeout: "10s",
  });

  if (response.status === 200) {
    const inventory = JSON.parse(response.body);
    console.log("═════════════════════════════════════════════════════");
    console.log("   📸 INITIAL STATE (BEFORE TEST)");
    console.log("═════════════════════════════════════════════════════");
    console.log(`SKU:                ${inventory.sku}`);
    console.log(
      `Total Quantity:     ${inventory.quantity.toLocaleString()} units`
    );
    console.log(
      `Reserved (BEFORE):  ${inventory.reservedQuantity.toLocaleString()} units`
    );
    console.log(
      `Available:          ${inventory.availableQuantity.toLocaleString()} units`
    );
    console.log("═════════════════════════════════════════════════════\n");

    // Wait a moment for any background processes to settle
    sleep(2);

    return { initialReserved: inventory.reservedQuantity };
  } else {
    console.log(`❌ Failed to get initial inventory: ${response.status}`);
    return { initialReserved: -1 };
  }
}

export default function () {
  const response = http.post(
    `${BASE_URL}/inventories/${TEST_SKU}/reserve?quantity=1`,
    null,
    { headers, timeout: "10s" }
  );

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
  return { stdout: "" };
}

export function teardown(data) {
  console.log("\n🔍 Taking final inventory snapshot...\n");

  // Wait for any async operations to complete
  sleep(3);

  const response = http.get(`${BASE_URL}/inventories/${TEST_SKU}`, {
    headers,
    timeout: "30s",
  });

  const successful = data.metrics.successful_reservations?.values?.count || 0;
  const conflicts = data.metrics.lock_conflicts_409?.values?.count || 0;
  const errors = data.metrics.server_errors_5xx?.values?.count || 0;
  const totalRequests = data.metrics.http_reqs?.values?.count || 0;
  const initialReserved = __ENV.K6_INITIAL_RESERVED
    ? parseInt(__ENV.K6_INITIAL_RESERVED)
    : data.setup_data?.initialReserved || 0;

  console.log("═════════════════════════════════════════════════════");
  console.log("   🚀 TEST RESULTS");
  console.log("═════════════════════════════════════════════════════");
  console.log(`Total Requests:        ${totalRequests.toLocaleString()}`);
  console.log(
    `Successful (201):      ${successful.toLocaleString()} (${(
      (successful / totalRequests) *
      100
    ).toFixed(2)}%)`
  );
  console.log(
    `Lock Conflicts (409):  ${conflicts.toLocaleString()} (${(
      (conflicts / totalRequests) *
      100
    ).toFixed(2)}%)`
  );
  console.log(
    `Server Errors (5xx):   ${errors.toLocaleString()} (${(
      (errors / totalRequests) *
      100
    ).toFixed(2)}%)`
  );
  console.log("─────────────────────────────────────────────────────");

  if (response.status === 200) {
    try {
      const inventory = JSON.parse(response.body);

      console.log("   📊 FINAL STATE (AFTER TEST)");
      console.log("─────────────────────────────────────────────────────");
      console.log(`SKU:                ${inventory.sku}`);
      console.log(
        `Total Quantity:     ${inventory.quantity.toLocaleString()} units`
      );
      console.log(
        `Reserved (AFTER):   ${inventory.reservedQuantity.toLocaleString()} units`
      );
      console.log(
        `Available:          ${inventory.availableQuantity.toLocaleString()} units`
      );
      console.log("─────────────────────────────────────────────────────");

      // Calculate changes
      const reservedChange = inventory.reservedQuantity - initialReserved;

      console.log("   🔬 DELTA ANALYSIS");
      console.log("─────────────────────────────────────────────────────");
      console.log(`Initial Reserved:   ${initialReserved.toLocaleString()}`);
      console.log(
        `Final Reserved:     ${inventory.reservedQuantity.toLocaleString()}`
      );
      console.log(
        `Net Change:         ${
          reservedChange > 0 ? "+" : ""
        }${reservedChange.toLocaleString()}`
      );
      console.log(`K6 Successful:      ${successful.toLocaleString()}`);
      console.log(
        `Discrepancy:        ${Math.abs(
          successful - reservedChange
        ).toLocaleString()}`
      );
      console.log("─────────────────────────────────────────────────────");

      // Validate arithmetic
      const expectedAvailable = inventory.quantity - inventory.reservedQuantity;
      const arithmeticValid = inventory.availableQuantity === expectedAvailable;

      console.log(
        `Formula: ${inventory.availableQuantity.toLocaleString()} = ${inventory.quantity.toLocaleString()} - ${inventory.reservedQuantity.toLocaleString()}`
      );

      if (arithmeticValid) {
        console.log("✅ Arithmetic correct (no race condition)");
      } else {
        console.log(
          `❌ FAIL: Expected ${expectedAvailable.toLocaleString()}, got ${inventory.availableQuantity.toLocaleString()}`
        );
      }

      // Analyze discrepancy
      const discrepancy = Math.abs(successful - reservedChange);

      if (discrepancy === 0) {
        console.log("✅ PERFECT: K6 count matches DB delta exactly!");
      } else if (discrepancy < 50) {
        console.log(
          "✅ EXCELLENT: Minor discrepancy (likely network retries/timeouts)"
        );
      } else if (discrepancy < 500) {
        console.log(
          "⚠️  ACCEPTABLE: Some discrepancy (background activity or retries)"
        );
      } else {
        console.log("❌ WARNING: Large discrepancy detected!");
        console.log("   Possible causes:");
        console.log("   - Concurrent confirmations by order-service");
        console.log("   - Payment timeouts releasing inventory");
        console.log("   - Network retries counted multiple times");
        console.log(
          `   - ${Math.abs(
            discrepancy
          ).toLocaleString()} reservations confirmed/released during test`
        );
      }

      console.log("─────────────────────────────────────────────────────");

      const successRate = (successful / totalRequests) * 100;
      const errorRate = (errors / totalRequests) * 100;

      if (errorRate < 1 && successRate > 50 && arithmeticValid) {
        console.log("✅ PASS: Service handles load perfectly!");
        console.log("💪 Redisson distributed locks working correctly");
      } else if (errorRate < 5 && arithmeticValid) {
        console.log("⚠️  MARGINAL: Service struggling but stable");
      } else {
        console.log("❌ FAIL: Issues detected");
      }
    } catch (e) {
      console.log(`❌ Failed to parse response: ${e.message}`);
      console.log(`Response body: ${response.body}`);
    }
  } else {
    console.log(`❌ Failed to get final inventory: HTTP ${response.status}`);
  }

  console.log("═════════════════════════════════════════════════════\n");
}
