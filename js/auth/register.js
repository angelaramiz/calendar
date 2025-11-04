/**
 * REGISTER MODULE
 * Handles user registration with username validation
 */

import { supabase } from '../supabase-client.js';

// DOM Elements
const registerForm = document.getElementById('register-form');
const usernameInput = document.getElementById('username');
const emailInput = document.getElementById('email');
const nameInput = document.getElementById('name');
const passwordInput = document.getElementById('password');
const confirmPasswordInput = document.getElementById('confirm-password');
const termsCheckbox = document.getElementById('accept-terms');
const togglePasswordBtn = document.getElementById('toggle-password');
const toggleConfirmBtn = document.getElementById('toggle-confirm');
const registerBtn = document.getElementById('register-btn');

// Password strength elements
const passwordStrengthDiv = document.getElementById('password-strength');
const strengthFill = document.getElementById('strength-fill');
const strengthText = document.getElementById('strength-text');

// Debounce timer for username check
let usernameCheckTimer = null;

/**
 * Initialize register page
 */
function initRegister() {
    setupEventListeners();
}

/**
 * Setup all event listeners
 */
function setupEventListeners() {
    // Form submission
    registerForm.addEventListener('submit', handleRegister);

    // Username validation (debounced)
    usernameInput.addEventListener('input', handleUsernameInput);

    // Password strength indicator
    passwordInput.addEventListener('input', updatePasswordStrength);

    // Toggle password visibility
    togglePasswordBtn.addEventListener('click', () => togglePassword(passwordInput, togglePasswordBtn));
    toggleConfirmBtn.addEventListener('click', () => togglePassword(confirmPasswordInput, toggleConfirmBtn));

    // Real-time password match validation
    confirmPasswordInput.addEventListener('input', validatePasswordMatch);
}

/**
 * Handle username input with debouncing
 */
function handleUsernameInput(e) {
    const username = e.target.value.trim();

    // Clear previous timer
    clearTimeout(usernameCheckTimer);

    // Reset validation state
    usernameInput.setCustomValidity('');

    if (username.length < 3) return;

    // Check username availability after 500ms of no typing
    usernameCheckTimer = setTimeout(() => checkUsernameAvailability(username), 500);
}

/**
 * Check if username is available
 */
async function checkUsernameAvailability(username) {
    try {
        const { data, error } = await supabase
            .from('users')
            .select('username')
            .eq('username', username)
            .limit(1);

        if (error) throw error;

        if (data && data.length > 0) {
            usernameInput.setCustomValidity('Este usuario ya existe');
            usernameInput.reportValidity();
        } else {
            usernameInput.setCustomValidity('');
        }
    } catch (err) {
        console.error('Error checking username:', err);
    }
}

/**
 * Update password strength indicator
 */
function updatePasswordStrength() {
    const password = passwordInput.value;

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
    const password = passwordInput.value;
    const confirmPassword = confirmPasswordInput.value;

    if (confirmPassword === '') {
        confirmPasswordInput.setCustomValidity('');
        return;
    }

    if (password !== confirmPassword) {
        confirmPasswordInput.setCustomValidity('Las contraseñas no coinciden');
        confirmPasswordInput.reportValidity();
    } else {
        confirmPasswordInput.setCustomValidity('');
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
 * Handle register form submission
 */
async function handleRegister(e) {
    e.preventDefault();

    // Get form values
    const username = usernameInput.value.trim();
    const emailRaw = emailInput ? emailInput.value.trim() : '';
    const name = nameInput.value.trim();
    const password = passwordInput.value;
    const confirmPassword = confirmPasswordInput.value;

    // Validate
    if (!username || !password) {
        showError('Por favor completa todos los campos obligatorios');
        return;
    }

    if (username.length < 3) {
        showError('El usuario debe tener al menos 3 caracteres');
        return;
    }

    if (password.length < 6) {
        showError('La contraseña debe tener al menos 6 caracteres');
        return;
    }

    if (password !== confirmPassword) {
        showError('Las contraseñas no coinciden');
        return;
    }

    if (!termsCheckbox.checked) {
        showError('Debes aceptar los términos de servicio');
        return;
    }

    // Show loading state
    setLoading(true);

    try {
        // Check username one more time
        const { data: existingUsers, error: checkError } = await supabase
            .from('users')
            .select('username')
            .eq('username', username)
            .limit(1);

        if (checkError) throw checkError;

        if (existingUsers && existingUsers.length > 0) {
            showError('Este usuario ya existe. Por favor elige otro.');
            setLoading(false);
            return;
        }

        // Determinar canal: teléfono o email
        const syntheticDomain = 'noemail.local';
        const email = emailRaw || `${username}@${syntheticDomain}`;

        // Registro por email
        const { data: signUpData, error: signUpError } = await supabase.auth.signUp({
            email,
            password,
            options: { data: { username, name } }
        });
        if (signUpError) throw signUpError;

        const authUser = signUpData.user;
        const hasSession = !!signUpData.session;

        // Crear perfil en tabla public.users cuando sea posible
        if (authUser) {
            await upsertProfile(authUser.id, username, name || username, email);
        }

        if (hasSession && authUser) {
            await handleSuccessfulRegistration({ id: authUser.id, username, name: name || username });
        } else {
            const isSynthetic = email.endsWith(`@${syntheticDomain}`);
            if (isSynthetic) {
                await Swal.fire({
                    icon: 'warning',
                    title: 'Configuración requerida',
                    html: 'Estás usando un email técnico. Para iniciar sesión sin confirmar correo, <strong>desactiva Email Confirmations</strong> en Supabase (Auth > Providers > Email).',
                    confirmButtonText: 'Entendido'
                });
                window.location.href = '../index.html';
            } else {
                await Swal.fire({
                    icon: 'info',
                    title: 'Verifica tu correo',
                    html: `Te enviamos un enlace a <strong>${email}</strong> para activar tu cuenta.`,
                    confirmButtonText: 'Entendido'
                });
                window.location.href = '../index.html';
            }
        }

    } catch (err) {
        console.error('Registration error:', err);
        showError('Error al crear la cuenta. Por favor intenta nuevamente.');
        setLoading(false);
    }
}

async function upsertProfile(id, username, name, email) {
    const { error: profileError } = await supabase
        .from('users')
        .upsert({
            id,
            username,
            name,
            email: email || null,
            settings: { theme: 'light', notifications: true, language: 'es' },
            password_hash: 'SUPABASE_AUTH'
        }, { onConflict: 'id' });
    if (profileError) console.warn('No se pudo crear/actualizar perfil en users:', profileError.message);
}

/**
 * Hash password using SHA-256
 * WARNING: This is NOT secure for production!
 * Use bcrypt or argon2 on the backend instead
 */
// Ya no se usa hashing manual: Supabase Auth maneja el almacenamiento seguro de contraseñas

/**
 * Handle successful registration
 */
async function handleSuccessfulRegistration(user) {
    // Create session (auto-login)
    const session = {
        userId: user.id,
        username: user.username,
        name: user.name,
        loginTime: new Date().toISOString()
    };

    localStorage.setItem('calendar_session', JSON.stringify(session));

    // Show success message
    await Swal.fire({
        icon: 'success',
        title: '¡Cuenta creada!',
        text: `Bienvenido ${user.name}`,
        timer: 2000,
        showConfirmButton: false
    });

    // Redirect to main
    window.location.href = '../routes/main.html';
}

/**
 * Set loading state
 */
function setLoading(loading) {
    registerBtn.disabled = loading;
    
    const btnText = registerBtn.querySelector('.btn-text');
    const btnLoader = registerBtn.querySelector('.btn-loader');

    if (loading) {
        btnText.style.display = 'none';
        btnLoader.style.display = 'inline-block';
        
        // Disable all inputs
        [usernameInput, nameInput, passwordInput, confirmPasswordInput, termsCheckbox].forEach(input => {
            input.disabled = true;
        });
    } else {
        btnText.style.display = 'inline';
        btnLoader.style.display = 'none';
        
        // Enable all inputs
        [usernameInput, nameInput, passwordInput, confirmPasswordInput, termsCheckbox].forEach(input => {
            input.disabled = false;
        });
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
document.addEventListener('DOMContentLoaded', initRegister);
