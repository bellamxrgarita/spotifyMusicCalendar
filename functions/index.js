const { onRequest } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");
const axios = require("axios");

admin.initializeApp();

// securely access secrets set via Firebase CLI cuz i inserted it there
// i decided to integrate this with the Firebase Blaze plan treating this as a personal project of something i've been wanting to do for a while
const SPOTIFY_CLIENT_ID = defineSecret("SPOTIFY_CLIENT_ID");
const SPOTIFY_CLIENT_SECRET = defineSecret("SPOTIFY_CLIENT_SECRET");

exports.exchangeSpotifyCode = onRequest(
  {
    secrets: [SPOTIFY_CLIENT_ID, SPOTIFY_CLIENT_SECRET],
  },
  async (req, res) => {
    const { code, redirect_uri, uid } = req.body;

    if (!code || !redirect_uri || !uid) {
      logger.error("Missing required fields", req.body);
      return res.status(400).json({ error: "Missing required fields: code, redirect_uri, or uid" });
    }

    const clientId = SPOTIFY_CLIENT_ID.value();
    const clientSecret = SPOTIFY_CLIENT_SECRET.value();
    const authHeader = Buffer.from(`${clientId}:${clientSecret}`).toString("base64");

    logger.info("Starting token exchange", { uid, redirect_uri });

    try {
      const response = await axios.post(
        "https://accounts.spotify.com/api/token",
        new URLSearchParams({
          grant_type: "authorization_code",
          code,
          redirect_uri,
        }),
        {
          headers: {
            Authorization: `Basic ${authHeader}`,
            "Content-Type": "application/x-www-form-urlencoded",
          },
        }
      );

      const { access_token, refresh_token, expires_in } = response.data;

      // save tokens to Realtime Database under users/{uid}/spotify_auth
      await admin.database().ref(`/users/${uid}/spotify_auth`).set({
        access_token,
        refresh_token,
        expires_in,
        fetched_at: Date.now(),
      });

      logger.info("Spotify token exchange successful", { uid });

      res.status(200).json({
        access_token,
        refresh_token,
        expires_in,
        status: "success",
      });
    } catch (err) {
      logger.error("Spotify token exchange failed", err.response?.data || err.message);
      res.status(500).json({
        error: "Spotify token exchange failed",
        details: err.response?.data || err.message,
      });
    }
  }
);

// this is to refresh the spotify token after its expired
exports.refreshSpotifyToken = onRequest(
  { secrets: [SPOTIFY_CLIENT_ID, SPOTIFY_CLIENT_SECRET] },
  async (req, res) => {
    const { refresh_token } = req.body;

    if (!refresh_token) {
      return res.status(400).json({ error: "Missing refresh_token" });
    }

    const clientId = SPOTIFY_CLIENT_ID.value();
    const clientSecret = SPOTIFY_CLIENT_SECRET.value();
    const authHeader = Buffer.from(`${clientId}:${clientSecret}`).toString("base64");

    try {
      const response = await axios.post(
        "https://accounts.spotify.com/api/token",
        new URLSearchParams({
          grant_type: "refresh_token",
          refresh_token
        }),
        {
          headers: {
            Authorization: `Basic ${authHeader}`,
            "Content-Type": "application/x-www-form-urlencoded"
          }
        }
      );

      const { access_token, expires_in } = response.data;
      res.status(200).json({ access_token, expires_in });
    } catch (err) {
      res.status(500).json({ error: "Refresh token exchange failed", details: err.response?.data || err.message });
    }
  }
);

