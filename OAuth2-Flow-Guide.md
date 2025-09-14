# OAuth2 Flow Guide for Frontend Applications

## 🔄 **OAuth2 Flow Overview**

The OAuth2 implementation supports both **traditional server-side** and **modern frontend** applications with a hybrid approach that provides maximum flexibility. Here's how each flow works:

## 📱 **Frontend Application Flow (Recommended)**

### **Step 1: Get Authorization URL**
```bash
GET /api/auth/oauth2/authorize/google
```

**Response:**
```json
{
  "authorizationUrl": "http://localhost:8081/oauth2/authorization/google",
  "provider": "google",
  "state": "random-state-uuid",
  "redirectUri": "http://localhost:8081/api/auth/oauth2/callback"
}
```

### **Step 2: Redirect User to Authorization URL**
```javascript
// Frontend JavaScript
const response = await fetch('/api/auth/oauth2/authorize/google');
const data = await response.json();

// Store state for verification
localStorage.setItem('oauth2_state', data.state);

// Redirect user to OAuth2 provider
window.location.href = data.authorizationUrl;
```

### **Step 3: Handle OAuth2 Callback**
The OAuth2 provider will redirect to: `http://localhost:8081/api/auth/oauth2/callback`

**Two possible response formats:**

#### **Option A: Tokens in URL Parameters (Current Implementation)**
The callback URL will contain tokens as query parameters:
```
http://localhost:8081/api/auth/oauth2/callback?access_token=eyJhbGciOiJIUzI1NiJ9...&refresh_token=uuid&token_type=Bearer&expires_in=900000&user_id=123
```

#### **Option B: JSON Response (Alternative)**
If the callback is accessed via AJAX, it returns JSON:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "uuid",
  "tokenType": "Bearer",
  "expiresIn": 900000,
  "refreshExpiresIn": 604800000,
  "userId": 123,
  "username": "john.doe",
  "email": "john.doe@gmail.com",
  "emailVerified": true
}
```

### **Step 4: Store Tokens and Redirect**
```javascript
// In your callback page
const urlParams = new URLSearchParams(window.location.search);
const accessToken = urlParams.get('access_token');
const refreshToken = urlParams.get('refresh_token');

if (accessToken) {
  // Store tokens
  localStorage.setItem('accessToken', accessToken);
  localStorage.setItem('refreshToken', refreshToken);
  
  // Redirect to main app
  window.location.href = '/';
} else {
  // Handle error
  const error = urlParams.get('error');
  console.error('OAuth2 error:', error);
}
```

## 🖥️ **Traditional Server-Side Flow**

### **Step 1: Direct Redirect**
```bash
GET /oauth2/authorization/google
```
This directly redirects to Google's OAuth2 authorization server.

### **Step 2: OAuth2 Callback**
Google redirects to: `http://localhost:8081/login/oauth2/code/google`

### **Step 3: Spring Security Processing**
Spring Security processes the callback and redirects to the configured success handler, which then redirects to:
`http://localhost:8081/api/auth/oauth2/callback?access_token=...&refresh_token=...`

### **Step 4: Final Redirect**
The success handler redirects to the callback URL with tokens in query parameters for frontend consumption.

## 🔧 **Configuration**

### **OAuth2 Provider Configuration**
```yaml
# application.yml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID:your-google-client-id}
            client-secret: ${GOOGLE_CLIENT_SECRET:your-google-client-secret}
            scope:
              - openid
              - profile
              - email
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            client-name: Google
          github:
            client-id: ${GITHUB_CLIENT_ID:your-github-client-id}
            client-secret: ${GITHUB_CLIENT_SECRET:your-github-client-secret}
            scope:
              - user:email
              - read:user
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            client-name: GitHub
          facebook:
            client-id: ${FACEBOOK_CLIENT_ID:your-facebook-client-id}
            client-secret: ${FACEBOOK_CLIENT_SECRET:your-facebook-client-secret}
            scope:
              - email
              - public_profile
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            client-name: Facebook
        provider:
          google:
            authorization-uri: https://accounts.google.com/o/oauth2/v2/auth
            token-uri: https://oauth2.googleapis.com/token
            user-info-uri: https://www.googleapis.com/oauth2/v2/userinfo
            user-name-attribute: sub
          github:
            authorization-uri: https://github.com/login/oauth/authorize
            token-uri: https://github.com/login/oauth/access_token
            user-info-uri: https://api.github.com/user
            user-name-attribute: id

app:
  oauth2:
    authorizedRedirectUris: ${OAUTH2_REDIRECT_URI:http://localhost:8081/api/auth/oauth2/callback}
```

### **CORS Configuration**
```yaml
app:
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:3001}
    allowed-methods: GET,POST,PUT,DELETE,OPTIONS
    allowed-headers: "*"
    allow-credentials: true
```

## 📱 **Mobile Application Flow**

### **Step 1: Use Mobile OAuth2 Libraries**
For mobile apps, use OAuth2 libraries like:
- **React Native**: `react-native-oauth2`
- **Flutter**: `oauth2` package
- **iOS**: `OAuthSwift`
- **Android**: `AppAuth`

### **Step 2: Get Authorization URL**
```bash
GET /api/auth/oauth2/authorize/google?clientId=mobile-app
```

### **Step 3: Handle OAuth2 in Mobile App**
```javascript
// React Native example
import { authorize } from 'react-native-app-auth';

const config = {
  issuer: 'https://accounts.google.com',
  clientId: 'your-google-client-id',
  redirectUrl: 'com.yourapp://oauth2callback',
  scopes: ['openid', 'profile', 'email'],
};

const result = await authorize(config);
// result.accessToken contains the OAuth2 access token
```

### **Step 4: Exchange for App Tokens**
```bash
POST /api/auth/oauth2/mobile/google
Content-Type: application/json

{
  "accessToken": "google-oauth2-access-token",
  "idToken": "google-id-token"
}
```

## 🛡️ **Security Considerations**

### **1. State Parameter**
Always use the `state` parameter to prevent CSRF attacks:
```javascript
const state = generateRandomString();
localStorage.setItem('oauth2_state', state);

// Verify state in callback
const receivedState = urlParams.get('state');
if (receivedState !== localStorage.getItem('oauth2_state')) {
  throw new Error('Invalid state parameter');
}
```

### **2. PKCE (Proof Key for Code Exchange)**
For mobile and SPA applications, implement PKCE:
```javascript
const codeVerifier = generateCodeVerifier();
const codeChallenge = generateCodeChallenge(codeVerifier);

// Include code_challenge in authorization request
const authUrl = `${baseUrl}/oauth2/authorization/google?code_challenge=${codeChallenge}&code_challenge_method=S256`;
```

### **3. Token Storage**
- **Web Apps**: Use `httpOnly` cookies or secure storage
- **Mobile Apps**: Use secure keychain/keystore
- **Never store tokens in localStorage for production**

## 🔍 **Testing the Flow**

### **1. Test Authorization URL Generation**
```bash
curl -X GET "http://localhost:8081/api/auth/oauth2/authorize/google"
```

### **2. Test OAuth2 Callback**
```bash
# This will be called by the OAuth2 provider
curl -X GET "http://localhost:8081/api/auth/oauth2/callback?code=authorization-code&state=state-value"
```

### **3. Test Mobile OAuth2**
```bash
curl -X POST "http://localhost:8081/api/auth/oauth2/mobile/google" \
  -H "Content-Type: application/json" \
  -d '{
    "accessToken": "google-access-token",
    "idToken": "google-id-token"
  }'
```

## 🚨 **Common Issues & Solutions**

### **Issue 1: CORS Errors**
**Solution**: Configure CORS properly in `application.yml`:
```yaml
app:
  cors:
    allowed-origins: http://localhost:3000,http://localhost:3001
```

### **Issue 2: Redirect URI Mismatch**
**Solution**: Ensure redirect URIs match exactly in OAuth2 provider configuration.

### **Issue 3: State Parameter Missing**
**Solution**: Always include and verify the state parameter.

### **Issue 4: Token Storage Issues**
**Solution**: Use secure storage methods appropriate for your platform.

## 📚 **Frontend Integration Examples**

### **React Example**
```jsx
import React, { useEffect, useState } from 'react';

const OAuth2Login = () => {
  const [loading, setLoading] = useState(false);

  const handleGoogleLogin = async () => {
    setLoading(true);
    try {
      const response = await fetch('/api/auth/oauth2/authorize/google');
      const data = await response.json();
      
      // Store state
      localStorage.setItem('oauth2_state', data.state);
      
      // Redirect to OAuth2 provider
      window.location.href = data.authorizationUrl;
    } catch (error) {
      console.error('OAuth2 error:', error);
      setLoading(false);
    }
  };

  return (
    <button onClick={handleGoogleLogin} disabled={loading}>
      {loading ? 'Loading...' : 'Login with Google'}
    </button>
  );
};
```

### **Vue.js Example**
```vue
<template>
  <button @click="handleGoogleLogin" :disabled="loading">
    {{ loading ? 'Loading...' : 'Login with Google' }}
  </button>
</template>

<script>
export default {
  data() {
    return {
      loading: false
    };
  },
  methods: {
    async handleGoogleLogin() {
      this.loading = true;
      try {
        const response = await fetch('/api/auth/oauth2/authorize/google');
        const data = await response.json();
        
        localStorage.setItem('oauth2_state', data.state);
        window.location.href = data.authorizationUrl;
      } catch (error) {
        console.error('OAuth2 error:', error);
        this.loading = false;
      }
    }
  }
};
</script>
```

## 🎯 **Best Practices**

1. **Always use HTTPS in production**
2. **Implement proper error handling**
3. **Use secure token storage**
4. **Implement token refresh logic**
5. **Add proper logging and monitoring**
6. **Test with different OAuth2 providers**
7. **Implement proper logout functionality**
8. **Use environment variables for configuration**

This OAuth2 implementation provides a robust, secure, and flexible authentication system that works with both traditional web applications and modern frontend frameworks! 🚀
