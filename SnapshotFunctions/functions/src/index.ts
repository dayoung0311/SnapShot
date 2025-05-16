import {
  onDocumentCreated,
  FirestoreEvent,
} from "firebase-functions/v2/firestore";
import { setGlobalOptions } from "firebase-functions/v2";
import * as logger from "firebase-functions/logger";
import * as admin from "firebase-admin";
import { QueryDocumentSnapshot } from "firebase-admin/firestore";
import {
  Messaging,
  Message,
} from "firebase-admin/messaging";

admin.initializeApp();

setGlobalOptions({ region: "asia-northeast3" });

const db: admin.firestore.Firestore = admin.firestore();
const messaging: Messaging = admin.messaging();

export const sendPushOnNewNotification = onDocumentCreated(
  "notifications/{notificationId}",
  async (event: FirestoreEvent<QueryDocumentSnapshot | undefined, { notificationId: string }>) => {
    const snapshot = event.data;

    if (!snapshot) {
      logger.log("Document snapshot is undefined.");
      return;
    }
    const notificationData = snapshot.data();

    if (!notificationData) {
      logger.warn("Notification document data is empty.", { structuredData: true });
      return;
    }

    const userId: string = notificationData.userId;
    const content: string =
      notificationData.content || "새로운 알림이 도착했습니다."; // 큰따옴표로 변경 (lint --fix)
    const notificationType: string =
      notificationData.notificationType || "general"; // 큰따옴표로 변경 (lint --fix)
    const targetId: string = notificationData.targetId || "";

    if (!userId) {
      logger.error("Missing userId in notification data:", notificationData);
      return;
    }

    logger.info(
      `Processing notification for user: ${userId}, Type: ${notificationType}` +
      `, Content: ${content}`, // 큰따옴표로 변경 (lint --fix)
      { structuredData: true }
    );

    try {
      // 1. 수신자의 FCM 토큰 가져오기
      const userTokenRef = db.collection("user_tokens").doc(userId);
      const userTokenDoc = await userTokenRef.get();

      if (!userTokenDoc.exists) {
        logger.warn(`FCM token document not found for user: ${userId}`, { userId });
        return;
      }

      const tokenData = userTokenDoc.data();
      if (!tokenData || !tokenData.token || tokenData.token === "") {
        logger.warn(
          `FCM token value is missing or empty for user: ${userId}`, // 큰따옴표로 변경 (lint --fix)
          { userId }
        );
        return;
      }
      const userFcmToken: string = tokenData.token;
      logger.info(`Found FCM token for user ${userId}`, { userId });

      // 2. FCM 메시지 구성
      const message: Message = {
        token: userFcmToken,
        notification: {
          title: "SnapShot",
          body: content,
        },
        data: {
          userId: userId,
          notificationType: notificationType,
          targetId: targetId,
          click_action: "FLUTTER_NOTIFICATION_CLICK",
        },
        android: {
          priority: "high",
          ttl: 60 * 60 * 24 * 1000,
        },
      };


      // 4. 메시지 발송
      logger.info(`Sending FCM message for user ${userId}`, { userId });
      const response = await messaging.send(message);
      logger.info("FCM send response (Message ID):", { response, userId });

      // 5. 발송 결과 처리
      logger.info(`Message send attempt finished for user ${userId}`, { userId });

      return;
    } catch (error) {
      logger.error("Error processing notification:", error, { userId });

      // 에러 타입 검사 및 처리 (TS2352 오류 수정)
      // 먼저 error가 FirebaseError와 유사한 구조인지 확인 (code 속성 존재 여부 등)
      if (error && typeof error === "object" && "code" in error) {
        const errorCode = (error as { code: string }).code; // error 객체에서 code 속성을 직접 읽음

        if (
          errorCode === "messaging/invalid-registration-token" ||
          errorCode === "messaging/registration-token-not-registered"
        ) {
          logger.warn(
            `Invalid token detected for user ${userId} during send. Deleting token.`, // 큰따옴표로 변경 (lint --fix)
            { userId }
          );
          await db.collection("user_tokens").doc(userId).delete();
        }
      } else if (error instanceof Error) {
        // 일반 Error 객체인 경우 (FirebaseError가 아닐 때)
        logger.error("A non-Firebase error occurred:", error.message, { userId });
      } else {
        // 그 외 알 수 없는 에러
        logger.error("An unknown error occurred during notification processing.", { userId });
      }
      return;
    }
  }
);