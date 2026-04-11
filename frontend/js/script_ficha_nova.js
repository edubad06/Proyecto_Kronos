//DOM
const btn_ue = document.querySelector("#pestana-ue");
const btn_jaciment = document.querySelector("#pestana-yacimiento");
const btn_sector = document.querySelector("#pestana-sector");
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



//FUNCIONES
// Canvia de pestanya activa
const cambiarPestana = function (id_pestana) {
    document.querySelectorAll('.boton-selector-pestana').forEach(boton => boton.classList.remove('activa'));
    document.querySelectorAll('.bloque-pestana').forEach(formulario => formulario.classList.remove('activa'));

    if (id_pestana) {
        const btn = document.getElementById('pestana-' + id_pestana);
        const form = document.getElementById('formulario-' + id_pestana);
        if (btn) btn.classList.add('activa');
        if (form) form.classList.add('activa');
    }
};

// Comprova la URL carregada per obrir la pestanya correcta
window.onload = function () {
    const parametrosUrl = new URLSearchParams(window.location.search);
    const idA_ubrir = parametrosUrl.get('tab');
    if (idA_ubrir) {
        cambiarPestana(idA_ubrir);
    }
};

btn_ue.addEventListener("click", function () {
    cambiarPestana();
    btn_ue.classList.add('activa');
    form_ue.classList.add('activa');
});

btn_jaciment.addEventListener("click", function () {
    cambiarPestana();
    btn_jaciment.classList.add('activa');
    form_jaciment.classList.add('activa');
});

btn_sector.addEventListener("click", function () {
    cambiarPestana();
    btn_sector.classList.add('activa');
    form_sector.classList.add('activa');
});

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



