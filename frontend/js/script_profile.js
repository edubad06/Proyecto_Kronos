// DOM
const inputNom = document.getElementById('input-nom');
const inputEmail = document.getElementById('input-email');
const inputPasswdActual = document.getElementById('input-passwd-actual');
const inputPasswdNova = document.getElementById('input-passwd-nova');
const inputPasswdRepe = document.getElementById('input-passwd-repetir');
const enlacePassActual = document.querySelector(".ver-passwd-actual");//iconEye
const enlacePassNova = document.querySelector(".ver-passwd-nova");//iconEye
const enlacePassRepe = document.querySelector(".ver-passwd-repe");//iconEye
const btnDesar = document.querySelector('.boton-guardar-perfil');
const nomPerfil = document.querySelector('.nombre-usuario-perfil');

// CARGAR DATOS DEL USUARIO

//onAuthStateChanged espera a que Firebase confirme si hay un usuario logueado.
auth.onAuthStateChanged(async function(user) {
    
    if (user) {
        //relleno los campos con los datos del usuario
        inputEmail.value = user.email;
        const nom = sessionStorage.getItem("nom"); //el nombre lo cogemos de sessionStorage que guardamos al hacer login
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
                const passwdRepetir = inputPasswdRepe.value;

                if (passwdActual || passwdNova || passwdRepetir) {
                    if (passwdNova !== passwdRepetir) {
                        alert("Les contrasenyes noves no coincideixen");
                        return;
                    }
                    if (passwdNova.length < 6) {
                        alert("La contrasenya ha de tenir mínim 6 caràcters");
                        return;
                    }
                    //Firebase requiere reautenticar antes de cambiar la contraseña
                    //porque es una operación sensible de seguridad
                    const credential = firebase.auth.EmailAuthProvider.credential(
                        user.email,
                        passwdActual
                    );
                    await user.reauthenticateWithCredential(credential);
                    await user.updatePassword(passwdNova);
                    //limpiamos campos
                    inputPasswdActual.value = '';
                    inputPasswdNova.value = '';
                    inputPasswdRepe.value = '';
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

//funciones para ver la contraseña 
enlacePassActual.addEventListener('click', function(e) {
    e.preventDefault();
    if (inputPasswdActual.type === "password") {
        inputPasswdActual.type = "text";
        this.textContent = "Amagar contrasenya";
    } else {
        inputPasswdActual.type = "password";
        this.textContent = "Mostrar contrasenya";
    }
});

enlacePassNova.addEventListener('click', function(e) {
    e.preventDefault();
    if (inputPasswdNova.type === "password") {
        inputPasswdNova.type = "text";
        this.textContent = "Amagar contrasenya";
    } else {
        inputPasswdNova.type = "password";
        this.textContent = "Mostrar contrasenya";
    }
});

enlacePassRepe.addEventListener('click', function(e) {
    e.preventDefault();
    if (inputPasswdRepe.type === "password") {
        inputPasswdRepe.type = "text";
        this.textContent = "Amagar contrasenya";
    } else {
        inputPasswdRepe.type = "password";
        this.textContent = "Mostrar contrasenya";
    }
});