//DOM
const btnLogin = document.querySelector(".boton-enviar-login");
const inputEmail = document.querySelector(".correo");
const inputPassword = document.querySelector(".passwd");
const iconEye = document.querySelector(".material-symbols-outlined"); 

//evento de click en el botón de login
btnLogin.addEventListener("click", async function(event) {
    event.preventDefault(); // evitar que recargue la página
    
    const email = inputEmail.value;
    const password = inputPassword.value;

    try {
        //inicio sesión con Firebase Authentication
        const userCredential = await auth.signInWithEmailAndPassword(email, password);
        const user = userCredential.user;
        
        //guardo el email y el token en la sessionStorage
        //el token lo necesito para las peticiones a la API de AWS
        //uso sessionStorage porque cuando se cierra el navegador se borra automáticamente
        sessionStorage.setItem("email", user.email);
        const token = await user.getIdToken();
        sessionStorage.setItem("token", token);
        sessionStorage.setItem("uid", user.uid);

        //busco el rol y el nombre del usuario y lo guardo en la sessionStorage
        const usuariQuery = await db.collection('usuaris').where('email', '==', user.email).get();
        if (!usuariQuery.empty) {
            const usuariData = usuariQuery.docs[0].data();
            sessionStorage.setItem("rol", usuariData.rol);
            sessionStorage.setItem("nom", usuariData.nom);
        }

        //redirijo a la librería
        window.location.assign("libreria.html");

    } catch (error) {
        alert("Correu o contrasenya incorrectes");
        console.error(error);
    }
});

//función para ver la contraseña a través del icono del ojo
iconEye.addEventListener('click', function(){ 
    inputPassword.type = inputPassword.type === "password" ? "text" : "password";
});

//para que funcione el botón de iniciar sesión al darle al enter (llama arriba)
document.addEventListener('keydown', function(event) {
    if (event.key === 'Enter') {
        btnLogin.click();
    }
});