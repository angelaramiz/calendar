/**
 * WEBAUTHN / BIOMETRIC AUTH MODULE FOR WEBSITES
 * Permite la autenticación con huella dactilar, FaceID, o Windows Hello usando WebAuthn.
 */

export function isBiometricSupported() {
    return window.PublicKeyCredential !== undefined &&
           typeof window.PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable === 'function';
}

export async function isPlatformAuthenticatorAvailable() {
    if (!isBiometricSupported()) return false;
    try {
        return await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable();
    } catch (e) {
        return false;
    }
}

/**
 * Guarda credenciales cifradas localmente y registra la opción biométrica
 */
export async function enableBiometricLogin(userId, username, email) {
    if (!await isPlatformAuthenticatorAvailable()) {
        return { success: false, error: 'Hardware biométrico no disponible' };
    }

    try {
        const challenge = new Uint8Array(32);
        window.crypto.getRandomValues(challenge);

        const userIdBuffer = new TextEncoder().encode(userId);

        const creationOptions = {
            challenge: challenge,
            rp: {
                name: "CalendarFinance Web",
                id: window.location.hostname
            },
            user: {
                id: userIdBuffer,
                name: email || username,
                displayName: username
            },
            pubKeyCredParams: [{ alg: -7, type: "public-key" }, { alg: -257, type: "public-key" }],
            authenticatorSelection: {
                authenticatorAttachment: "platform",
                userVerification: "preferred"
            },
            timeout: 60000
        };

        const credential = await navigator.credentials.create({ publicKey: creationOptions });
        if (credential) {
            const bioSession = {
                userId,
                username,
                email,
                credId: arrayBufferToBase64(credential.rawId),
                enabledAt: new Date().toISOString()
            };
            localStorage.setItem('calendar_biometric_session', JSON.stringify(bioSession));
            return { success: true };
        }
    } catch (err) {
        console.warn('Biometric registration skipped or canceled:', err);
        // Sin credential real no se guarda nada: volver a intentar tras login con contraseña
        localStorage.removeItem('calendar_biometric_session');
        return { success: false, error: 'Registro biométrico cancelado o no disponible' };
    }
    return { success: false, error: 'No se pudo registrar la biometría' };
}

/**
 * Autentica al usuario usando el sensor biométrico
 */
export async function authenticateWithBiometrics() {
    const bioDataStr = localStorage.getItem('calendar_biometric_session');
    if (!bioDataStr) {
        throw new Error('No hay sesión biométrica configurada en este dispositivo.');
    }

    const bioData = JSON.parse(bioDataStr);

    if (await isPlatformAuthenticatorAvailable() && bioData.credId) {
        try {
            const challenge = new Uint8Array(32);
            window.crypto.getRandomValues(challenge);

            const getOptions = {
                challenge: challenge,
                allowCredentials: [{
                    id: base64ToArrayBuffer(bioData.credId),
                    type: 'public-key'
                }],
                userVerification: 'preferred',
                timeout: 60000
            };

            const assertion = await navigator.credentials.get({ publicKey: getOptions });
            if (assertion) {
                return bioData;
            }
            throw new Error('No se pudo verificar la huella dactilar.');
        } catch (e) {
            console.warn('WebAuthn assertion failed:', e);
            throw new Error('No se pudo verificar la huella dactilar.');
        }
    }

    throw new Error('Biometría no disponible en este dispositivo.');
}

function arrayBufferToBase64(buffer) {
    let binary = '';
    const bytes = new Uint8Array(buffer);
    for (let i = 0; i < bytes.byteLength; i++) {
        binary += String.fromCharCode(bytes[i]);
    }
    return window.btoa(binary);
}

function base64ToArrayBuffer(base64) {
    const binaryString = window.atob(base64);
    const bytes = new Uint8Array(binaryString.length);
    for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
    }
    return bytes.buffer;
}
