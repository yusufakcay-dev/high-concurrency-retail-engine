import http from "k6/http";
import { check, sleep } from "k6";

// Configuration
const BASE_URL = "http://localhost:8080";
const JWT_TOKEN =
  "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiQURNSU4iLCJ1c2VySWQiOjEsInN1YiI6ImxldG9zaXByYSIsImlhdCI6MTc2NzU0ODA1NywiZXhwIjoxNzY3NjM0NDU3fQ.mhYgxOF22tDhX-0DVNGKO_WCMKb0l9GReGcUCB104MQ"; // Replace with a valid JWT token for authentication
const TEST_SKU = "test";

export const options = {
  stages: [
    { duration: "10s", target: 500 }, // Ramp up to 500 users in 10s
    { duration: "30s", target: 500 }, // Stay at 500 users for 30s
    { duration: "10s", target: 1000 }, // SPIKE to 1000 users in 10s
    { duration: "20s", target: 1000 }, // Hold spike at 1000 users for 20s
    { duration: "10s", target: 0 }, // Ramp down to 0
  ],
  thresholds: {
    http_req_duration: ["p(95)<3000"], // 95% under 3s (higher due to contention)
    http_req_failed: ["rate<0.5"], // Allow up to 50% lock conflicts
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

  // Small sleep to simulate realistic behavior
  sleep(0.05);
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
