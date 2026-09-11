/**
 * LOGIN MODULE
 * Handles user authentication with username/password
 */

import { supabase } from '../supabase-client.js';
import { isPlatformAuthenticatorAvailable, enableBiometricLogin, authenticateWithBiometrics } from './webauthn.js';

// DOM Elements
const loginForm = document.getElementById('login-form');
const usernameInput = document.getElementById('username');
const passwordInput = document.getElementById('password');
const rememberCheckbox = document.getElementById('remember-me');
const togglePasswordBtn = document.getElementById('toggle-password');
const loginBtn = document.getElementById('login-btn');
const biometricLoginBtn = document.getElementById('biometric-login-btn');

/**
 * Initialize login page
 */
async function initLogin() {
    // Check if already logged in
    checkExistingSession();

    // Load remembered username if exists
    loadRememberedUsername();

    // Setup event listeners
    setupEventListeners();

    // Check biometric availability (solo si hay un registro real con credId)
    try {
        const available = await isPlatformAuthenticatorAvailable();
        let hasBioSession = false;
        try {
            const bioRaw = localStorage.getItem('calendar_biometric_session');
            hasBioSession = !!(bioRaw && JSON.parse(bioRaw).credId);
        } catch (_) { hasBioSession = false; }
        if (available && hasBioSession && biometricLoginBtn) {
            biometricLoginBtn.style.display = 'block';
            biometricLoginBtn.addEventListener('click', handleBiometricLogin);
        }
    } catch (e) {
        console.warn('Biometric check failed:', e);
    }
}

/**
 * Check if user already has a valid session
 */
async function checkExistingSession() {
    try {
        // Si ya hay una sesión de Supabase, asegúrate de tener calendar_session y redirige
        const { data: { user } } = await supabase.auth.getUser();
        if (user) {
            let sessionData = localStorage.getItem('calendar_session');
            if (!sessionData) {
                // Completar datos básicos desde perfil
                let username = user.user_metadata?.username || '';
                let name = user.user_metadata?.name || '';
                try {
                    const { data: profileData } = await supabase
                        .from('users')
                        .select('username, name')
                        .eq('id', user.id)
                        .limit(1);
                    if (profileData && profileData.length > 0) {
                        const profile = profileData[0];
                        username = profile.username || username;
                        name = profile.name || name || username;
                    }
                } catch (_) { /* ignore */ }

                const session = {
                    userId: user.id,
                    username: username || user.email,
                    name: name || username || user.email,
                    loginTime: new Date().toISOString()
                };
                localStorage.setItem('calendar_session', JSON.stringify(session));
            }
            window.location.href = 'routes/main.html';
            return;
        } else {
            // Sin sesión de Supabase: limpiar cualquier calendar_session obsoleta
            localStorage.removeItem('calendar_session');
        }
    } catch (err) {
        console.error('Error checking session:', err);
        localStorage.removeItem('calendar_session');
    }
}

/**
 * Load remembered username from localStorage
 */
function loadRememberedUsername() {
    const rememberedUser = localStorage.getItem('remembered_username');
    if (rememberedUser) {
        usernameInput.value = rememberedUser;
        rememberCheckbox.checked = true;
        passwordInput.focus();
    }
}

/**
 * Setup all event listeners
 */
function setupEventListeners() {
    // Form submission
    if (loginForm) {
        loginForm.addEventListener('submit', handleLogin);
    }

    // Toggle password visibility
    if (togglePasswordBtn) {
        togglePasswordBtn.addEventListener('click', togglePasswordVisibility);
    }

    // Enter key on inputs
    [usernameInput, passwordInput].filter(Boolean).forEach(input => {
        input.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                if (loginForm) {
                    loginForm.requestSubmit();
                }
            }
        });
    });
}

/**
 * Handle login form submission
 */
async function handleLogin(e) {
    e.preventDefault();

    const username = usernameInput.value.trim();
    const password = passwordInput.value;

    // Validación básica
    if (!username || !password) {
        showError('Por favor completa todos los campos');
        return;
    }

    setLoading(true);

    try {
        // Si el input parece un email, iniciar sesión directamente con email
        if (username.includes('@')) {
            const { data: signInData, error: signInError } = await supabase.auth.signInWithPassword({
                email: username,
                password
            });
            if (signInError || !signInData?.user) {
                const msg = (signInError?.message || '').toLowerCase();
                if (msg.includes('email not confirmed') || msg.includes('not confirmed')) {
                    showError('Tu email no está confirmado. Revisa tu bandeja y confirma tu cuenta.');
                } else if (msg.includes('invalid login credentials') || msg.includes('invalid')) {
                    showError('Usuario o contraseña incorrectos');
                } else {
                    showError('No se pudo iniciar sesión. ' + (signInError?.message || ''));
                }
                setLoading(false);
                return;
            }

            // Auto-provisionar perfil si no existe y usarlo
            const profileUser = await ensureUserProfile(signInData.user);
            await handleSuccessfulLogin({ id: signInData.user.id, username: profileUser.username || signInData.user.email, name: profileUser.name || profileUser.username });
            return;
        }

        // Caso: login por username (buscar email en perfil)
        const { data: users, error: queryError } = await supabase
            .from('users')
            .select('id, username, name, email')
            .ilike('username', username)
            .limit(1);
        if (queryError) throw queryError;
        if (!users || users.length === 0) {
            showError('No encontramos ese usuario. Si te registraste con correo, inicia sesión con tu email la primera vez para asociar tu nombre de usuario.');
            setLoading(false);
            return;
        }

        const profile = users[0];
        if (!profile.email) {
            await Swal.fire({
                icon: 'info',
                title: 'Tu cuenta necesita email',
                html: 'Este usuario fue creado antes de migrar a Supabase Auth.<br>Por favor, vuelve a registrarte con tu correo en "Crear Cuenta".',
                confirmButtonText: 'Ir a crear cuenta'
            });
            window.location.href = 'routes/register.html';
            return;
        }

        const { data: signInData, error: signInError } = await supabase.auth.signInWithPassword({
            email: profile.email,
            password
        });
        if (signInError || !signInData?.user) {
            const msg = (signInError?.message || '').toLowerCase();
            if (msg.includes('email not confirmed') || msg.includes('not confirmed')) {
                showError('Tu email no está confirmado. Revisa tu bandeja y confirma tu cuenta.');
            } else if (msg.includes('invalid login credentials') || msg.includes('invalid')) {
                showError('Usuario o contraseña incorrectos');
            } else {
                showError('No se pudo iniciar sesión. ' + (signInError?.message || ''));
            }
            setLoading(false);
            return;
        }

    // Asegurar perfil por si fue creado fuera del flujo de registro
    const ensuredProfile = await ensureUserProfile(signInData.user, profile);
    await handleSuccessfulLogin({ id: signInData.user.id, username: ensuredProfile.username, name: ensuredProfile.name || ensuredProfile.username });
    } catch (err) {
        console.error('Login error:', err);
        showError('Error al iniciar sesión. Por favor intenta nuevamente.');
        setLoading(false);
    }
}

/**
 * Verify password against hash
 * Note: For production, use bcrypt on the backend
 * This is a simplified version using SHA-256 for demo purposes
 */
// Ya no se usa verificación local de hash: Supabase Auth valida credenciales
// (función eliminada)

/**
 * Handle successful login
 */
async function handleSuccessfulLogin(user) {
    // Create session
    const session = {
        userId: user.id,
        username: user.username,
        name: user.name,
        loginTime: new Date().toISOString()
    };

    // Save session
    localStorage.setItem('calendar_session', JSON.stringify(session));

    // Migrar configuraciones de notificaciones si existen globales (V2)
    try {
        const notifKey = `notificationSettings:${user.id}`;
        const globalNotif = localStorage.getItem('notificationSettings');
        if (globalNotif && !localStorage.getItem(notifKey)) {
            localStorage.setItem(notifKey, globalNotif);
            localStorage.removeItem('notificationSettings');
        }

        const alertsKey = `eventAlerts:${user.id}`;
        const globalAlerts = localStorage.getItem('eventAlerts');
        if (globalAlerts && !localStorage.getItem(alertsKey)) {
            localStorage.setItem(alertsKey, globalAlerts);
            localStorage.removeItem('eventAlerts');
        }

        const readAlertsKey = `readAlerts:${user.id}`;
        const globalReadAlerts = localStorage.getItem('readAlerts');
        if (globalReadAlerts && !localStorage.getItem(readAlertsKey)) {
            localStorage.setItem(readAlertsKey, globalReadAlerts);
            localStorage.removeItem('readAlerts');
        }
        
        // Limpiar datos legacy V1 (events en localStorage) 
        localStorage.removeItem('events');
        localStorage.removeItem(`events:${user.id}`);
    } catch (e) {
        console.warn('Aviso: no se pudo migrar configuraciones:', e);
    }

    // Handle remember me
    if (rememberCheckbox.checked) {
        localStorage.setItem('remembered_username', user.username);
    } else {
        localStorage.removeItem('remembered_username');
    }

    // Activar soporte biométrico rápido en la web
    try {
        await enableBiometricLogin(user.id, user.username, user.email);
    } catch (e) {
        console.warn('Biometric registration info:', e);
    }

    // Show success message
    await Swal.fire({
        icon: 'success',
        title: '¡Bienvenido!',
        text: `Hola ${user.name || user.username}`,
        timer: 1500,
        showConfirmButton: false
    });

    // Redirect to main (V2 usa Supabase directamente, no necesita sincronización legacy)
    window.location.href = 'routes/main.html';
}

/**
 * Iniciar sesión rápido usando la huella / biometría del navegador
 */
async function handleBiometricLogin() {
    try {
        const bioSession = await authenticateWithBiometrics();
        if (!bioSession || !bioSession.userId) {
            throw new Error('No se pudo verificar la huella dactilar.');
        }
        // La biometría solo desbloquea: la sesión real debe existir en Supabase.
        // Si el registro se canceló alguna vez, no hay credId y se exige contraseña.
        const { data: { user } } = await supabase.auth.getUser();
        if (!user || (bioSession.userId !== user.id && bioSession.email !== user.email)) {
            throw new Error('Sesión caducada. Ingresa tu correo y contraseña una vez para reactivar la huella.');
        }
        if (bioSession && bioSession.userId) {
            const session = {
                userId: user.id,
                username: bioSession.username || user.email,
                name: bioSession.username || user.email,
                loginTime: new Date().toISOString()
            };
            localStorage.setItem('calendar_session', JSON.stringify(session));

            await Swal.fire({
                icon: 'success',
                title: '¡Autenticado con Biometría!',
                text: `Bienvenido de nuevo, ${bioSession.username || bioSession.email}`,
                timer: 1500,
                showConfirmButton: false
            });

            window.location.href = 'routes/main.html';
        }
    } catch (err) {
        Swal.fire({
            icon: 'error',
            title: 'Error de Autenticación Biométrica',
            text: err.message || 'No se pudo verificar la huella dactilar.'
        });
    }
}

/**
 * Asegura que exista un perfil en public.users para el auth user.
 * Si no existe, lo crea con username y name derivados de metadata o email.
 */
async function ensureUserProfile(authUser, fallbackProfile) {
    const email = authUser.email || fallbackProfile?.email || '';
    // Derivar username: metadata.username -> parte local del email -> 'user-<4>'
    let username = (authUser.user_metadata?.username || fallbackProfile?.username || (email ? email.split('@')[0] : '') || `user-${(authUser.id || '').slice(0,4)}`);
    let name = (authUser.user_metadata?.name || fallbackProfile?.name || username);

    try {
        // Intentar obtener el perfil sin forzar objeto único para evitar 406
        const { data: existingArr } = await supabase
            .from('users')
            .select('id, username, name')
            .eq('id', authUser.id)
            .limit(1);
        if (existingArr && existingArr.length > 0) {
            return existingArr[0];
        }
    } catch (_) { /* seguimos con upsert */ }

    // Crear/actualizar por id sin tocar usernames de otros
    const { data: upserted, error: upErr } = await supabase
        .from('users')
        .upsert({
            id: authUser.id,
            username,
            name,
            email,
            // Compatibilidad con esquema anterior (custom auth)
            // Este valor es un marcador; el login real lo gestiona Supabase Auth
            password_hash: 'SUPABASE_AUTH',
            settings: { theme: 'light', notifications: true, language: 'es' }
        }, { onConflict: 'id' })
        .select('username, name')
        .single();
    if (upErr) {
        console.warn('No se pudo asegurar perfil en users:', upErr.message);
        return { username: username || email, name };
    }
    return upserted;
}

/**
 * Toggle password visibility
 */
function togglePasswordVisibility() {
    const type = passwordInput.type === 'password' ? 'text' : 'password';
    passwordInput.type = type;
    togglePasswordBtn.textContent = type === 'password' ? '👁️' : '🙈';
}

/**
 * Set loading state
 */
function setLoading(loading) {
    loginBtn.disabled = loading;
    
    const btnText = loginBtn.querySelector('.btn-text');
    const btnLoader = loginBtn.querySelector('.btn-loader');

    if (loading) {
        btnText.style.display = 'none';
        btnLoader.style.display = 'inline-block';
        usernameInput.disabled = true;
        passwordInput.disabled = true;
    } else {
        btnText.style.display = 'inline';
        btnLoader.style.display = 'none';
        usernameInput.disabled = false;
        passwordInput.disabled = false;
    }
}

/**
 * Show error message
 */
function showError(message) {
    Swal.fire({
        icon: 'error',
        title: 'Error',
        text: message,
        confirmButtonColor: '#667eea'
    });
}

// Initialize on page load
document.addEventListener('DOMContentLoaded', initLogin);
