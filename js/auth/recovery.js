/**
 * RECOVERY MODULE
 * Handles password recovery process
 */

import { supabase } from '../supabase-client.js';

// DOM Elements
const recoveryForm = document.getElementById('recovery-form');
const usernameInput = document.getElementById('username');
const securityAnswerInput = document.getElementById('security-answer');
const newPasswordInput = document.getElementById('new-password');
const confirmNewPasswordInput = document.getElementById('confirm-new-password');

// Step containers
const step1 = document.getElementById('step-1');
const step2 = document.getElementById('step-2');
const step3 = document.getElementById('step-3');

// Buttons
const verifyBtn = document.getElementById('verify-btn');
const verifyAnswerBtn = document.getElementById('verify-answer-btn');
const resetBtn = document.getElementById('reset-btn');

// Toggle buttons
const toggleNewBtn = document.getElementById('toggle-new');
const toggleConfirmNewBtn = document.getElementById('toggle-confirm-new');

// Password strength elements
const passwordStrengthDiv = document.getElementById('password-strength');
const strengthFill = document.getElementById('strength-fill');
const strengthText = document.getElementById('strength-text');

// State
let currentUser = null;
let currentStep = 1;

/**
 * Initialize recovery page
 */
function initRecovery() {
    setupEventListeners();

    // Manejar el flujo cuando el usuario entra desde el enlace de recuperación por email
    try {
        supabase.auth.onAuthStateChange((event, session) => {
            if (event === 'PASSWORD_RECOVERY') {
                // Mostrar UI para establecer nueva contraseña
                flagRecoveryLinkArrival();
                showStep3();
            }
        });
    } catch (e) { /* ignore */ }

    // Si la URL ya trae parámetros de recuperación (hash o query) y la sesión temporal está activa, intentar avanzar.
    // Algunos navegadores pueden disparar el evento tarde; detectamos presencia de 'access_token' en el fragment/hash.
    const hash = window.location.hash;
    if (hash && /access_token=/.test(hash)) {
        // Pequeño retraso para dar tiempo al cliente a procesar tokens y disparar el evento
        setTimeout(() => {
            // Si aún no estamos en step 3, forzar
            if (currentStep !== 3) {
                flagRecoveryLinkArrival();
                showStep3();
            }
        }, 400);
    }
}

/**
 * Setup all event listeners
 */
function setupEventListeners() {
    // Step 1: Verify username
    recoveryForm.addEventListener('submit', handleStep1);

    // Step 2: Verify security answer (if implemented)
    verifyAnswerBtn.addEventListener('click', handleStep2);

    // Step 3: Reset password
    resetBtn.addEventListener('click', handleStep3);

    // Password strength indicator
    newPasswordInput.addEventListener('input', updatePasswordStrength);

    // Toggle password visibility
    toggleNewBtn.addEventListener('click', () => togglePassword(newPasswordInput, toggleNewBtn));
    toggleConfirmNewBtn.addEventListener('click', () => togglePassword(confirmNewPasswordInput, toggleConfirmNewBtn));

    // Password match validation
    confirmNewPasswordInput.addEventListener('input', validatePasswordMatch);
}

/**
 * Handle Step 1: Verify username
 */
async function handleStep1(e) {
    e.preventDefault();

    const username = usernameInput.value.trim();

    if (!username) {
        showError('Por favor ingresa tu usuario');
        return;
    }

    setLoadingButton(verifyBtn, true);

    try {
        // Si ingresó un correo directamente, enviar reset sin buscar perfil
        if (username.includes('@')) {
            const redirectTo = new URL('../routes/recob_pass.html', window.location.href).href;
            const { error: resetErr } = await supabase.auth.resetPasswordForEmail(username, { redirectTo });
            if (resetErr) throw resetErr;
            await Swal.fire({
                icon: 'success',
                title: 'Revisa tu correo',
                html: `Te enviamos un enlace a <strong>${username}</strong> para restablecer tu contraseña.`,
                confirmButtonText: 'Entendido'
            });
            // Quedarse en esta página; el enlace te traerá de vuelta con sesión temporal
            return;
        }

        // Quitar flujo por teléfono: Solo correo o búsqueda por username

        // Buscar email por username
        const { data: users, error } = await supabase
            .from('users')
            .select('id, username, name, email')
            .eq('username', username)
            .limit(1);

        if (error) throw error;

        if (!users || users.length === 0 || !users[0].email) {
            showError('Usuario no encontrado. Si te registraste con correo, ingrésalo directamente para enviar el enlace.');
            setLoadingButton(verifyBtn, false);
            return;
        }

        currentUser = users[0];

        const syntheticDomain = 'noemail.local';
        if (currentUser.email && currentUser.email.endsWith(`@${syntheticDomain}`)) {
            await Swal.fire({
                icon: 'info',
                title: 'Recuperación no disponible',
                html: 'Tu cuenta usa un email técnico, por lo que no podemos enviarte un enlace de recuperación. <br>Regístrate de nuevo con un email real o contacta al administrador.',
                confirmButtonText: 'Volver al inicio de sesión'
            });
            window.location.href = '../index.html';
            return;
        }

        // Enviar email de recuperación vía Supabase Auth
    const redirectTo = new URL('../routes/recob_pass.html', window.location.href).href;
    const { error: resetError } = await supabase.auth.resetPasswordForEmail(currentUser.email, { redirectTo });
        if (resetError) throw resetError;

        await Swal.fire({
            icon: 'success',
            title: 'Revisa tu correo',
            html: `Te enviamos un enlace a <strong>${currentUser.email}</strong> para restablecer tu contraseña.`,
            confirmButtonText: 'Entendido'
        });
        // Quedarse en esta página; el enlace te traerá de vuelta con sesión temporal

    } catch (err) {
        console.error('Error verifying username:', err);
        showError('Error al verificar el usuario. Por favor intenta nuevamente.');
        setLoadingButton(verifyBtn, false);
    }
}

/**
 * Show warning and proceed to Step 3 (no security question)
 */
// Ya no se usa pregunta de seguridad: Supabase Auth gestiona el flujo por email

/**
 * Show Step 2: Security question
 */
function showStep2(question) { /* deprecated */ }

/**
 * Handle Step 2: Verify security answer
 */
async function handleStep2() { /* deprecated */ }

/**
 * Show Step 3: Reset password
 */
function showStep3() {
    currentStep = 3;
    // Ocultar pasos anteriores
    if (step1) step1.style.display = 'none';
    if (step2) step2.style.display = 'none';
    if (step3) step3.style.display = 'block';
}

/**
 * Agrega un banner informativo cuando se llegó desde el correo de recuperación
 */
function flagRecoveryLinkArrival() {
    const form = document.getElementById('recovery-form');
    if (!form || form.querySelector('.recovery-banner')) return;
    const div = document.createElement('div');
    div.className = 'success-message recovery-banner';
    div.style.marginBottom = '1rem';
    div.innerHTML = '<strong>🔐 Enlace verificado.</strong> Ingresa tu nueva contraseña abajo.';
    step3?.insertBefore(div, step3.firstChild);
}

/**
 * Handle Step 3: Reset password
 */
async function handleStep3() {
    const newPassword = newPasswordInput.value.trim();
    const confirmPassword = confirmNewPasswordInput.value.trim();

    if (!newPassword || newPassword.length < 6) {
        showError('La contraseña debe tener al menos 6 caracteres.');
        return;
    }
    if (newPassword !== confirmPassword) {
        showError('Las contraseñas no coinciden.');
        return;
    }

    setLoadingButton(resetBtn, true);
    try {
        const { error } = await supabase.auth.updateUser({ password: newPassword });
        if (error) throw error;
        await handleSuccessfulReset();
        try { await supabase.auth.signOut(); } catch (_) {}
    } catch (err) {
        console.error('Error updating password:', err);
        showError('No se pudo actualizar la contraseña. Intenta nuevamente.');
    } finally {
        setLoadingButton(resetBtn, false);
    }
}

/**
 * Handle successful password reset
 */
async function handleSuccessfulReset() {
    await Swal.fire({
        icon: 'success',
        title: '¡Contraseña restablecida!',
        text: 'Tu contraseña ha sido actualizada exitosamente',
        confirmButtonColor: '#667eea'
    });

    // Redirect to login
    window.location.href = '../index.html';
}

/**
 * Hash password using SHA-256
 * WARNING: This is NOT secure for production!
 * Use bcrypt or argon2 on the backend instead
 */
// Hash local eliminado: Auth maneja el flujo de reseteo de contraseña por email

/**
 * Update password strength indicator
 */
function updatePasswordStrength() {
    const password = newPasswordInput.value;

    if (password.length === 0) {
        passwordStrengthDiv.style.display = 'none';
        return;
    }

    passwordStrengthDiv.style.display = 'block';

    const strength = calculatePasswordStrength(password);

    // Update visual indicator
    strengthFill.className = 'strength-fill ' + strength.class;
    strengthText.textContent = `Seguridad: ${strength.label}`;
}

/**
 * Calculate password strength
 */
function calculatePasswordStrength(password) {
    let score = 0;

    // Length
    if (password.length >= 6) score++;
    if (password.length >= 8) score++;
    if (password.length >= 12) score++;

    // Contains lowercase
    if (/[a-z]/.test(password)) score++;

    // Contains uppercase
    if (/[A-Z]/.test(password)) score++;

    // Contains number
    if (/\d/.test(password)) score++;

    // Contains special char
    if (/[^a-zA-Z0-9]/.test(password)) score++;

    // Determine strength
    if (score <= 3) {
        return { class: 'weak', label: 'débil' };
    } else if (score <= 5) {
        return { class: 'medium', label: 'media' };
    } else {
        return { class: 'strong', label: 'fuerte' };
    }
}

/**
 * Validate password match
 */
function validatePasswordMatch() {
    const password = newPasswordInput.value;
    const confirmPassword = confirmNewPasswordInput.value;

    if (confirmPassword === '') {
        confirmNewPasswordInput.setCustomValidity('');
        return;
    }

    if (password !== confirmPassword) {
        confirmNewPasswordInput.setCustomValidity('Las contraseñas no coinciden');
        confirmNewPasswordInput.reportValidity();
    } else {
        confirmNewPasswordInput.setCustomValidity('');
    }
}

/**
 * Toggle password visibility
 */
function togglePassword(input, button) {
    const type = input.type === 'password' ? 'text' : 'password';
    input.type = type;
    button.textContent = type === 'password' ? '👁️' : '🙈';
}

/**
 * Set loading state for a button
 */
function setLoadingButton(button, loading) {
    button.disabled = loading;
    
    const btnText = button.querySelector('.btn-text');
    const btnLoader = button.querySelector('.btn-loader');

    if (loading) {
        btnText.style.display = 'none';
        btnLoader.style.display = 'inline-block';
    } else {
        btnText.style.display = 'inline';
        btnLoader.style.display = 'none';
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
document.addEventListener('DOMContentLoaded', initRecovery);
