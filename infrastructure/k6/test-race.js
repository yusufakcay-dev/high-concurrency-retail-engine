import http from "k6/http";
import { check } from "k6";

// Configuration
const BASE_URL = "https://retail.yusufakcay.dev";
const JWT_TOKEN =
  "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiQURNSU4iLCJ1c2VySWQiOjEsInN1YiI6ImxldG9zaXByYSIsImlhdCI6MTc2NzcwOTA2MSwiZXhwIjoxNzY3Nzk1NDYxfQ.UCEnmvzcMj7XqqA9F8XLIkdlCtHqvQIprkwE5q3W1Dk"; // Replace with a valid JWT token for authentication
const TEST_SKU = "test";

export const options = {
  discardResponseBodies: true, // Critical for high RPS
  scenarios: {
    // 🚀 NEW EXECUTOR: Controls RPS, not Users
    api_stress: {
      executor: "ramping-arrival-rate",

      // Allocate enough VUs to handle the load (k6 will reuse them)
      preAllocatedVUs: 1000,
      maxVUs: 5000, // Ceiling to prevent crashing your computer

      stages: [
        // 1. JIT WARM UP: Gentle load to compile Java byte-code
        { target: 500, duration: "60s" }, // Hold 500 RPS for 1 min

        // 2. RAMP UP: Climb to the summit
        { target: 5000, duration: "30s" }, // Go to 5000 RPS

        // 3. SUSTAIN: Hold the high note
        { target: 5000, duration: "1m" }, // Stay at 5000 RPS

        // 4. COOLDOWN
        { target: 0, duration: "10s" },
      ],
    },
  },
  thresholds: {
    http_req_duration: ["p(95)<3000"],
    // We expect conflicts (409), but 500 errors should be low
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
    console.log("✅ Reserved 1 unit");
  } else if (response.status === 409) {
    console.log("⏱️  Lock conflict (expected under high load)");
  } else {
    console.error(`❌ Status: ${response.status} - ${response.body}`);
  }
}

export function teardown() {
  console.log("\n� STRESS TEST COMPLETE - Validating results...\n");

  const response = http.get(`${BASE_URL}/inventories/${TEST_SKU}`, { headers });

  if (response.status === 200) {
    const inventory = JSON.parse(response.body);

    console.log("═══════════════════════════════════════════");
    console.log("   🚀 EXTREME LOAD RACE CONDITION TEST    ");
    console.log("═══════════════════════════════════════════");
    console.log(`SKU:                ${inventory.sku}`);
    console.log(`Initial Quantity:   1000 units`);
    console.log(`Current Quantity:   ${inventory.quantity} units`);
    console.log(`Reserved:           ${inventory.reservedQuantity} units`);
    console.log(`Available:          ${inventory.availableQuantity} units`);
    console.log("───────────────────────────────────────────");

    // Validate formula: availableQuantity = quantity - reservedQuantity
    const expectedAvailable = inventory.quantity - inventory.reservedQuantity;
    const isValid = inventory.availableQuantity === expectedAvailable;

    console.log(
      `Formula Check: ${inventory.availableQuantity} = ${inventory.quantity} - ${inventory.reservedQuantity}`
    );

    if (isValid) {
      console.log("✅ PASS: Formula is correct");
    } else {
      console.log(
        `❌ FAIL: Expected ${expectedAvailable}, got ${inventory.availableQuantity}`
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

    // Check for overselling
    if (inventory.reservedQuantity <= 1000) {
      console.log("✅ PASS: No overselling (reserved ≤ 1000)");
    } else {
      console.log(
        `❌ FAIL: OVERSELLING DETECTED! Reserved ${inventory.reservedQuantity} > 1000`
      );
    }

    console.log("═══════════════════════════════════════════\n");

    if (
      isValid &&
      inventory.availableQuantity >= 0 &&
      inventory.reservedQuantity <= 1000
    ) {
      console.log("🎉 TEST PASSED: Distributed locks survived the spike!");
      console.log("💪 Race conditions prevented under extreme load!\n");
    } else {
      console.log("💥 TEST FAILED: Race condition detected!\n");
    }
  }
}
