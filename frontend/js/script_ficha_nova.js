//DOM
const form_ue = document.querySelector("#formulario-ue");
const form_jaciment = document.querySelector("#formulario-yacimiento");
const form_sector = document.querySelector("#formulario-sector");
const btn_guardar = document.querySelector(".boton-accion-guardar");
const dropZone = document.querySelector(".dropZone");

//variables  
let map;

//inicializar mapa 
const initMapa = function() {
    map = L.map('map').setView([41.402765, 2.194551], 8); //setView([latitud,longitud],zoom)

    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
    }).addTo(map);
    
}
initMapa();

//arrastrar imágenes
const loadImages = function(files) {
    if (files && files.length > 0) {
        // Iteramos sobre todos los ficheros
        Array.from(files).forEach(file => {
            if (file.type.startsWith("image/")) {
                const reader = new FileReader(); //libreria de js que usa 3 métodos: onload, onroad y onerror
                //mientras se carga el fichero. Es decir, si funciona se mete aquí
                reader.onload = (event) => {
                    console.log("imagen cargada:", file.name); // comprueba si llega aquí
                    const imageUrl = event.target.result;
                    const img = document.createElement("img");
                    img.src = imageUrl;
                    dropZone.appendChild(img); //añadimos sin borrar las anteriores
                }
                //si hay un error mientras se carga el fichero se mete aqui
                reader.onerror = (error) => {
                    console.log("Error durante la carga de la imagen", error);
                }
                //te cargo el fichero, y si va bien se mete al onload
                reader.readAsDataURL(file);
            } else {
                alert(`El fitxer "${file.name}" no és una imatge`);
            }
        });
    }
};

//MOSTRAR FORMULARIO SEGÚN PARÁMETRO
/*lee el tab de la URL, oculta todos los formularios, y muestra solo el que corresponde.*/
const params = new URLSearchParams(window.location.search);
const tab = params.get('tab') || 'ue';
document.querySelectorAll('.bloque-pestana').forEach(f => f.style.display = 'none');
const formularioActivo = document.getElementById('formulario-' + tab);
if (formularioActivo) formularioActivo.style.display = 'block';

const sec_nom = document.querySelector(".sec-nom");
console.log(sec_nom.value);

/**
 * const afegirPuntuacio = async function(){
    const bodyPost = {
        "nomUsuario": jugador1.nom,
        "puntuacionMaxima": jugador1.partidaMasPunts,
        "totalPartidas": jugador1.totalPartidas,
        "partidasGanadas": jugador1.partidasGanadas,
        "fecha": fechaPartida,
        "partidaGanada": partidaGanada,
        "puntuacionPartida": jugador1.puntsPartidaActual
    }
    console.log("histórico que se guarda: ",bodyPost); //Object { nomUsuario: "arnau", puntuacionMaxima: 9, totalPartidas: 3, partidasGanadas: 2, fecha: "2026-01-31", partidaGanada: true, puntuacionPartida: 8 }
    try{
        const respuesta = await fetch ("http://127.0.0.1:8000/partida",{
            method:"POST",
            body: JSON.stringify(bodyPost),
            headers:{
                'Content-Type':'application/json'
            }
        });
        if (respuesta.ok){
            const body = await respuesta.json();
            console.log("POST correcto", body); //POST correcto Object { msg: "Histórico guardado correctamente" }
        }
    }
    catch(e){
        console.log("error al intentar introducir datos en la BBDD")
    }
};
 */

btn_guardar.addEventListener("click", function(){
    window.location.assign("libreria.html");
});

//ARRASTRAR Y SOLTAR DOCUMENTOS
dropZone.addEventListener("dragover", (event)=>{ //dragover = arrastrar
    event.preventDefault(); //para que no abra el documento que arrastramos
    console.log("estas arrastrando el documento");
});

dropZone.addEventListener("drop",(event)=>{ //drop = soltar
    event.preventDefault(); //para que no abra el documento que soltamos
    console.log("he dejado el documento");
    const files = event.dataTransfer.files; //para recuperar el fichero en binari
    loadImages(files); //nos genera un array de todos los ficheros que sueltas
});


