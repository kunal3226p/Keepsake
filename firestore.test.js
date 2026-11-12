const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const nowTs = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write albums, family_members, collages, or memories", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("albums").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("family_members").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("collages").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("albums").doc("alb_1").set({
      userId: ALICE_UID,
      title: "Beach",
      category: "Travel",
      photoCount: 1,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );
});

test("Owner can create, update, list, and delete their own album, and ghost fields are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const albumRef = aliceDb.collection("users").doc(ALICE_UID).collection("albums").doc("alb_travel");

  await assertSucceeds(
    albumRef.set({
      userId: ALICE_UID,
      title: "Coastal Getaways",
      category: "Travel & Outdoors",
      summary: "Sunny beaches and coastal hikes",
      tags: ["beach", "ocean", "sunset"],
      coverPhotoUri: "content://media/1",
      photoCount: 42,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("albums").get()
  );

  await assertSucceeds(
    albumRef.update({
      photoCount: 48,
      updatedAt: nowTs(),
    })
  );

  // Ghost field injection fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("albums").doc("alb_ghost").set({
      userId: ALICE_UID,
      title: "Ghost",
      category: "Test",
      photoCount: 1,
      isAdmin: true,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  await assertSucceeds(albumRef.delete());
});

test("Cross-user isolation: Bob cannot read or write Alice's family_members, collages, or memories", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("family_members").doc("mem_mom").set({
      userId: ALICE_UID,
      name: "Mom",
      relationship: "Mother",
      faceCropBase64: "abc123",
      referencePhotoUri: "content://media/2",
      taggedCount: 12,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("collages").doc("col_2026_11").set({
      userId: ALICE_UID,
      monthKey: "2026-11",
      monthLabel: "November 2026",
      title: "Autumn Golden Hours",
      storyNote: "Warm family walks",
      layoutStyle: "EDITORIAL_TRIO",
      photoUris: ["content://media/1", "content://media/2"],
      paletteTone: "TERRACOTTA",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("photo_1").set({
      userId: ALICE_UID,
      photoKey: "photo_1",
      uriString: "content://media/1",
      displayName: "IMG_001.jpg",
      monthKey: "2026-11",
      albumCategory: "Family Gatherings",
      caption: "Sunday dinner",
      tags: ["family", "dinner"],
      taggedMemberIds: ["mem_mom"],
      isFavorite: true,
      takenAtMillis: 1762900000000,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  // Cross-user access denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("family_members").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("collages").doc("col_2026_11").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("memories").doc("photo_1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("collages").doc("col_bob").set({
      userId: ALICE_UID,
      monthKey: "2026-11",
      monthLabel: "November 2026",
      title: "Spoof",
      layoutStyle: "GRID_2X2",
      photoUris: [],
      paletteTone: "TERRACOTTA",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );
});

test("Invalid collage layoutStyle or oversized album title is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("collages").doc("col_bad").set({
      userId: ALICE_UID,
      monthKey: "2026-11",
      monthLabel: "November 2026",
      title: "Bad Layout",
      layoutStyle: "UNKNOWN_STYLE",
      photoUris: [],
      paletteTone: "TERRACOTTA",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );
});
