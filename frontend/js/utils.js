// FUNCIONES COMPARTIDAS ENTRE PÁGINAS

/*
 * OCULTAR OPCIÓN DE JACIMENT EN EL MENÚ
 * Si el usuario no es director, oculta la opción de crear jaciment
 * en el desplegable del navbar. 
 */
const menuJacNav = document.querySelector('.menu-jac');
if (menuJacNav && sessionStorage.getItem("rol") !== 'director') {
    menuJacNav.style.display = 'none';
}

/*
 * SUBIR IMAGEN A S3
 * Sube una imagen al servidor de AWS S3 y guarda la URL pública en Firestore.
 * Parámetros:
 *   - file: el archivo de imagen seleccionado por el usuario
 *   - idDocument: el id del documento de Firestore donde guardar la URL
 *   - coleccio: la colección de Firestore ('sectors', 'jaciments', 'unitats_estratigrafiques')
 * Proceso:
 *   1. Pide una URL temporal a la API de AWS
 *   2. Sube el archivo binario a esa URL temporal (S3)
 *   3. Guarda la URL pública en el array imatges_urls del documento de Firestore
 */
const subirImatge = async function(file, idDocument, coleccio) {
    try {
        if (!auth.currentUser) {
            alert("Sessió caducada. Torna a iniciar sessió.");
            window.location.assign("index.html");
            return;
        }
        // Obtenemos el token de Firebase
        const token = await auth.currentUser.getIdToken();
        // PASO 1: Pedimos la URL temporal a la API
        const respostaUrl = await fetch('https://4qctvrdnjc.execute-api.us-east-1.amazonaws.com/prod/fotos', {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'X-Api-Key': AWS_API_KEY,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                file_name: file.name,
                file_type: file.type
            })
        });

        if (!respostaUrl.ok) throw new Error("Error obtenint la URL de pujada");
        const { upload_url, public_url } = await respostaUrl.json();
        // PASO 2: Subimos el archivo binario directamente a S3
        const respostaPujada = await fetch(upload_url, {
            method: 'PUT',
            headers: { 'Content-Type': file.type },
            body: file
        });

        if (!respostaPujada.ok) throw new Error("Error pujant la imatge a S3");
        // PASO 3: Guardamos la URL pública en Firestore
        await db.collection(coleccio).doc(idDocument).update({
            imatges_urls: firebase.firestore.FieldValue.arrayUnion(public_url)
        });

        console.log("Imatge pujada correctament:", public_url);
        return public_url;

    } catch (error) {
        console.error("Error pujant la imatge:", error);
        alert("Error en pujar la imatge");
    }
};

/*
 * CREAR ELEMENTO DE IMAGEN
 * Crea un contenedor HTML con una imagen, un botón para verla en grande
 * y un botón para eliminarla.
 * Parámetros:
 *   - url: la URL de la imagen (puede ser una URL de S3 o una URL temporal base64)
 *   - onEliminar: función callback que se ejecuta cuando el usuario confirma eliminar.
 *                 Si es null, el botón de eliminar no hace nada más que quitarla del DOM.
 */
const crearImatge = function(url, onEliminar) {
    const contenidor = document.createElement('div');
    contenidor.classList.add('contenidor-imatge');

    const img = document.createElement('img');
    img.src = url;
    img.style.width = '100px';
    img.style.height = '100px';
    img.style.objectFit = 'cover';
    img.style.borderRadius = '8px';
    img.style.cursor = 'pointer';

    // Ver en grande
    img.addEventListener('click', function() {
        document.getElementById('modal-img').src = url;
        document.querySelector('.modal-imatge').classList.add('actiu');
    });

    // Botón eliminar
    const btnEliminar = document.createElement('button');
    btnEliminar.classList.add('btn-eliminar-imatge');
    btnEliminar.textContent = '✕';
    btnEliminar.addEventListener('click', function(e) {
        e.stopPropagation(); // evitar que abra el modal
        if (confirm("Vols eliminar aquesta imatge?")) {
            
            contenidor.remove(); // quitar del DOM
            if (onEliminar) onEliminar(url); // callback para borrar de Firestore
            
        }
    });

    contenidor.appendChild(img);
    contenidor.appendChild(btnEliminar);
    return contenidor;
};

/*
 * CERRAR MODAL DE IMAGEN
 * Gestiona el cierre del modal que muestra las imágenes en grande.
 * Se cierra al hacer click en la X o fuera del modal.
 */
document.addEventListener('DOMContentLoaded', function() {
    const modalTancar = document.querySelector('.modal-tancar');
    const modal = document.querySelector('.modal-imatge');
    if (modalTancar) {
        modalTancar.addEventListener('click', function() {
            modal.classList.remove('actiu');
        });
    }
    // También cerrar clickando fuera
    if (modal) {
        modal.addEventListener('click', function(e) {
            if (e.target === modal) modal.classList.remove('actiu');
        });
    }
});

/*
 * CERRAR SESIÓN
 * cierra la sesión de Firebase, limpia el sessionStorage
 * y redirige a la página de login.
 */
const btnTancarSessio = document.querySelector('a[href="index.html"]');
if (btnTancarSessio) {
    btnTancarSessio.addEventListener('click', async function(event) {
        event.preventDefault();
        await auth.signOut();
        sessionStorage.clear();
        window.location.assign("index.html");
    });
}

/*
 * SINCRONIZAR CON ORACLE
 * Llama a la API de AWS para sincronizar los datos de Firebase con Oracle.
 * La API busca todos los documentos con sincronitzat: false,
 * los guarda en Oracle y los marca como sincronitzat: true.
 * Se llama automáticamente después de cada guardar o crear ficha.
 */
const sincronitzar = async function() {
    try {
        const token = sessionStorage.getItem("token");
        const resposta = await fetch('https://4qctvrdnjc.execute-api.us-east-1.amazonaws.com/prod/sync', {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'X-Api-Key': AWS_API_KEY
            }
        });
        console.log("Sync status:", resposta.status);
        const body = await resposta.json();
        console.log("Sync resposta:", body);
        if (!resposta.ok) throw new Error("Error sync");
        console.log("Sincronització correcta");
    } catch (error) {
        console.error("Error sincronitzant:", error);
    }
};

/*
 * MOSTRAR AVATAR DEL NAVBAR
 * Firebase llama a onAuthStateChanged automáticamente cuando carga la página
 * y sabe si hay un usuario logueado o no.
 * Si hay usuario, muestra la primera letra de su nombre en el avatar del navbar.
 *
 *
 * auth.onAuthStateChanged es un listener que Firebase llama automáticamente 
 * cada vez que cambia el estado de autenticación, es decir cuando el usuario inicia o cierra sesión. 
 * No hace falta llamarlo explícitamente, Firebase lo ejecuta solo cuando carga la página.
 */

auth.onAuthStateChanged(function(user) {
    if (!user) return; // si no hay usuario, no hacemos nada
    const nom = sessionStorage.getItem("nom");
    
    // Avatar pequeño del navbar
    const avatar = document.querySelector('.avatar-cabecera');
    if (avatar && nom) {
        avatar.textContent = nom.charAt(0).toUpperCase();
    }
});