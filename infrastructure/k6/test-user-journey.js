import http from "k6/http";
import { check, sleep } from "k6";
import { uuidv4 } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";

// Configuration
const BASE_URL = "https://retail.yusufakcay.dev";
const JWT_TOKEN =
  "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiQURNSU4iLCJ1c2VySWQiOjEsInN1YiI6ImxldG9zaXByYSIsImlhdCI6MTc2NzczODQyNiwiZXhwIjoxNzY3ODI0ODI2fQ.C0kG0jj6Q2qrGilAq-loQEWGrzYL77zW2_7A7a_y0VY";
const TEST_SKU = "test";

export const options = {
  scenarios: {
    complete_journey: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "20s", target: 30 },
        { duration: "1m", target: 30 },
        { duration: "10s", target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_duration: ["p(95)<3000"],
    checks: ["rate>0.90"], // 90% of checks should pass
  },
};

import { Counter } from "k6/metrics";

const successfulJourneys = new Counter("successful_journeys");
const failedJourneys = new Counter("failed_journeys");

export default function () {
  const uniqueId = uuidv4();
  const email = `user-${uniqueId}@test.com`;
  const username = `user_${uniqueId.substring(0, 8)}`;
  const password = "SecurePass123!";

  // ═══════════════════════════════════════════════════════════
  // STEP 1: Register a new user
  // ═══════════════════════════════════════════════════════════
  const registerPayload = JSON.stringify({
    username: username,
    password: password,
  });

  const registerResponse = http.post(
    `${BASE_URL}/auth/register`,
    registerPayload,
    {
      headers: { "Content-Type": "application/json" },
    }
  );

  const registerCheck = check(registerResponse, {
    "Registration successful": (r) => r.status === 200,
  });

  if (!registerCheck) {
    console.log(
      `❌ Registration failed: ${registerResponse.status} - ${registerResponse.body}`
    );
    failedJourneys.add(1);
    return;
  }

  sleep(0.5);

  // ═══════════════════════════════════════════════════════════
  // STEP 2: Login
  // ═══════════════════════════════════════════════════════════
  const loginPayload = JSON.stringify({
    username: username,
    password: password,
  });

  const loginResponse = http.post(`${BASE_URL}/auth/login`, loginPayload, {
    headers: { "Content-Type": "application/json" },
  });

  const loginCheck = check(loginResponse, {
    "Login successful": (r) => r.status === 200,
  });

  if (!loginCheck) {
    console.log(
      `❌ Login failed: ${loginResponse.status} - ${loginResponse.body}`
    );
    failedJourneys.add(1);
    return;
  }

  const loginData = JSON.parse(loginResponse.body);
  const userToken = loginData.token;

  const authHeaders = {
    "Content-Type": "application/json",
    Authorization: `Bearer ${userToken}`,
  };

  sleep(0.5);

  // ═══════════════════════════════════════════════════════════
  // STEP 3: Browse Products
  // ═══════════════════════════════════════════════════════════
  const productsResponse = http.get(`${BASE_URL}/products`, {
    headers: authHeaders,
  });

  const productsCheck = check(productsResponse, {
    "Products listed": (r) => r.status === 200,
  });

  if (!productsCheck) {
    console.log(
      `❌ Product listing failed: ${productsResponse.status} - ${productsResponse.body}`
    );
    failedJourneys.add(1);
    return;
  }

  sleep(1);

  // ═══════════════════════════════════════════════════════════
  // STEP 4: Create Order
  // ═══════════════════════════════════════════════════════════
  const orderPayload = JSON.stringify({
    userId: 1,
    customerEmail: email,
    amount: 149.99,
    items: [
      {
        sku: TEST_SKU,
        quantity: 1,
      },
    ],
  });

  const orderResponse = http.post(`${BASE_URL}/api/orders`, orderPayload, {
    headers: authHeaders,
  });

  const orderCheck = check(orderResponse, {
    "Order created": (r) => r.status === 201,
    "Payment URL present": (r) => {
      if (r.status !== 201) return false;
      const order = JSON.parse(r.body);
      return order.paymentUrl && order.paymentUrl.includes("stripe.com");
    },
  });

  if (!orderCheck) {
    console.log(
      `❌ Order creation failed: ${orderResponse.status} - ${orderResponse.body}`
    );
    failedJourneys.add(1);
    return;
  }

  const order = JSON.parse(orderResponse.body);

  sleep(0.5);

  // ═══════════════════════════════════════════════════════════
  // STEP 5: Verify Order Status
  // ═══════════════════════════════════════════════════════════
  const orderStatusResponse = http.get(`${BASE_URL}/api/orders/${order.id}`, {
    headers: authHeaders,
  });

  const statusCheck = check(orderStatusResponse, {
    "Order status retrieved": (r) => r.status === 200,
    "Order is PENDING": (r) => {
      if (r.status !== 200) return false;
      const orderData = JSON.parse(r.body);
      return orderData.status === "PENDING";
    },
  });

  if (statusCheck) {
    successfulJourneys.add(1);
    console.log(`✅ Complete journey succeeded for ${email}`);
  } else {
    failedJourneys.add(1);
    console.log(
      `⚠️  Journey completed with issues: ${orderStatusResponse.status}`
    );
  }
}

export function handleSummary(data) {
  const successful = data.metrics.successful_journeys?.values?.count || 0;
  const failed = data.metrics.failed_journeys?.values?.count || 0;
  const total = successful + failed;
  const successRate = total > 0 ? (successful / total) * 100 : 0;

  console.log("\n═══════════════════════════════════════════");
  console.log("   🚀 COMPLETE USER JOURNEY TEST RESULTS  ");
  console.log("═══════════════════════════════════════════");
  console.log(`Successful Journeys:    ${successful}`);
  console.log(`Failed Journeys:        ${failed}`);
  console.log("───────────────────────────────────────────");
  console.log(`Success Rate:           ${successRate.toFixed(2)}%`);
  console.log("───────────────────────────────────────────");

  if (successRate >= 90) {
    console.log("✅ PASS: End-to-end flow is reliable!");
    console.log("💪 All services working in harmony!\n");
  } else {
    console.log("⚠️  FAIL: Success rate below 90%");
    console.log("Check service health and dependencies\n");
  }

  console.log("═══════════════════════════════════════════\n");

  return {};
}
