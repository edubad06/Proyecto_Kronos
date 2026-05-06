// DOM
const inputNom = document.getElementById('input-nom');
const inputEmail = document.getElementById('input-email');
const inputPasswdActual = document.getElementById('input-passwd-actual');
const inputPasswdNova = document.getElementById('input-passwd-nova');
const inputPasswdRepe = document.getElementById('input-passwd-repetir');
//const iconEye = document.querySelector(".material-symbols-outlined"); 
const btnDesar = document.querySelector('.boton-guardar-perfil');
const nomPerfil = document.querySelector('.nombre-usuario-perfil');
const menuJac = document.querySelector('.menu-jac');


// Ocultar jaciment si no es director
const rol = sessionStorage.getItem("rol");
if (rol !== 'director') {
    menuJac.style.display = 'none';
}

// CARGAR DATOS DEL USUARIO

//const user = auth.currentUser;// auth.currentUser tiene el usuario logueado de Firebase Authentication
//import { getAuth, reauthenticateWithCredential, EmailAuthProvider, updatePassword } from "firebase/auth";

//const auth = getAuth();
//const user = auth.currentUser;

auth.onAuthStateChanged(async function(user) {
    
    if (user) {
        inputEmail.value = user.email;
        //el nombre lo cogemos de sessionStorage que guardamos al hacer login
        const nom = sessionStorage.getItem("nom");
        inputNom.value = nom || '';
        nomPerfil.textContent = nom || user.email;

        // GUARDAR CAMBIOS
        btnDesar.addEventListener('click', async function() {
            try {
                // CAMBIAR NOMBRE
                // Lo guardamos en Firestore en el documento del usuario
                const nom = inputNom.value.trim();
                if (nom) {
                    // Buscar el documento del usuario por email y actualizar el nom
                    const usuariQuery = await db.collection('usuaris')
                        .where('email', '==', user.email).get();
                    if (!usuariQuery.empty) {
                        await usuariQuery.docs[0].ref.update({ nom: nom }); //el primero de los resultados
                        sessionStorage.setItem("nom", nom); // actualizar sessionStorage
                        nomPerfil.textContent = nom;
                    }
                }

                // CAMBIAR CONTRASENYA
                // Solo si ha rellenado los campos de contraseña
                const passwdActual = inputPasswdActual.value;
                const passwdNova = inputPasswdNova.value;
                const passwdRepetir = inputPasswdRepetir.value;

                if (passwdActual || passwdNova || passwdRepetir) {
                    if (passwdNova !== passwdRepetir) {
                        alert("Les contrasenyes noves no coincideixen");
                        return;
                    }
                    if (passwdNova.length < 6) {
                        alert("La contrasenya ha de tenir mínim 6 caràcters");
                        return;
                    }
                    const credential = firebase.auth.EmailAuthProvider.credential(
                        user.email,
                        passwdActual
                    );
                    await user.reauthenticateWithCredential(credential);
                    await user.updatePassword(passwdNova);

                    inputPasswdActual.value = '';
                    inputPasswdNova.value = '';
                    inputPasswdRepetir.value = '';
                }

                alert("Perfil desat correctament!");

            } catch (error) {
                console.error("Error desant el perfil:", error);
                if (error.code === 'auth/wrong-password') {
                    alert("La contrasenya actual és incorrecta");
                } else if (error.code === 'auth/weak-password') {
                    alert("La contrasenya nova és massa feble");
                } else {
                    alert("Error en desar el perfil");
                }
            }
        });
    } else {
        window.location.assign("index.html");
    }
});

const enlacePassActual = document.querySelector(".ver-passwd-actual");
const enlacePassNova = document.querySelector(".ver-passwd-nova");
const enlacePassRepe = document.querySelector(".ver-passwd-repe");

enlacePassActual.addEventListener('click', function(){ 
    //lo convertimos en un input type="password", para que salgan puntitos
    //y al clickar el ojo se convierta en texto y viceversa
    if (inputPasswdActual.type==="password"){
        inputPasswdActual.setAttribute("type","text");
    }else{
        inputPasswdActual.setAttribute("type","password");
    }
});

enlacePassNova.addEventListener('click', function(){ 
    //lo convertimos en un input type="password", para que salgan puntitos
    //y al clickar el ojo se convierta en texto y viceversa
    if (inputPasswdNova.type==="password"){
        inputPasswdNova.setAttribute("type","text");
    }else{
        inputPasswdNova.setAttribute("type","password");
    }
});

enlacePassRepe.addEventListener('click', function(){ 
    //lo convertimos en un input type="password", para que salgan puntitos
    //y al clickar el ojo se convierta en texto y viceversa
    if (inputPasswdRepe.type==="password"){
        inputPasswdRepe.setAttribute("type","text");
    }else{
        inputPasswdRepe.setAttribute("type","password");
    }
});