
const firebaseConfig = {
  apiKey: "AIzaSyCddmLjM1PwmP0ZC3Pd9P6P9h8iuXUPHWg",
  authDomain: "kronos-e3625.firebaseapp.com",
  projectId: "kronos-e3625",
  storageBucket: "kronos-e3625.firebasestorage.app",
  messagingSenderId: "573047841332",
  appId: "1:573047841332:web:a2d4068cd4d31b6516e061",
  measurementId: "G-WJGGT2H6W9"
};

// Initialize Firebase
firebase.initializeApp(firebaseConfig);
const db = firebase.firestore();
const auth = firebase.auth();

const AWS_API_KEY = 'jFOzFueL9B8RB9MIzrSJ08CMFq73tG0f8bbeHJHf';

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

//funcion para hacer grande las imágenes
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

// Cerrar modal
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

//cerrar sesiÓn
const btnTancarSessio = document.querySelector('a[href="index.html"]');
if (btnTancarSessio) {
    btnTancarSessio.addEventListener('click', async function(event) {
        event.preventDefault();
        await auth.signOut();
        sessionStorage.clear();
        window.location.assign("index.html");
    });
}

//sincronizar con Oracle
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