const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");
admin.initializeApp();

exports.pushNotification = onDocumentCreated("notifications/{id}", async (event) => {
  const n = event.data.data();
  const t = await admin.firestore().doc(`fcmTokens/${n.toUid}`).get();
  if (!t.exists) return;
  await admin.messaging().send({
    token: t.data().token,
    notification: { title: "ReelChat", body: n.body },
  });
});
