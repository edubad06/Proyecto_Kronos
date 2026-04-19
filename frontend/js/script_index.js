const btnLogin = document.querySelector(".boton-enviar-login");
const inputEmail = document.querySelector(".correo");
const inputPassword = document.querySelector(".passwd");
const iconEye = document.querySelector(".material-symbols-outlined"); 

btnLogin.addEventListener("click", async function(event) {
    event.preventDefault(); // evitar que recargue la página
    
    const email = inputEmail.value;
    const password = inputPassword.value;

    try {
        const userCredential = await auth.signInWithEmailAndPassword(email, password);
        const user = userCredential.user;
        
        //guardo el token para usarlo en los fetch
        //uso sessionStorage para guardar el token, así cuando se cierra el navegador se borra automáticamente
        const token = await user.getIdToken();
        sessionStorage.setItem("token", token);
        sessionStorage.setItem("uid", user.uid);

        //redirijo a la librería
        window.location.assign("libreria.html");

    } catch (error) {
        alert("Correu o contrasenya incorrectes");
        console.error(error);
    }
});

iconEye.addEventListener('click', function(){ 
    //lo convertimos en un input type="password", para que salgan puntitos
    //y al clickar el ojo se convierta en texto y viceversa
    if (inputPassword.type==="password"){
        inputPassword.setAttribute("type","text");
    }else{
        inputPassword.setAttribute("type","password");
    }
});