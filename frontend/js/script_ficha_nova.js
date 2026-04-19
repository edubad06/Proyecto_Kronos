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


btn_guardar.addEventListener("click", async function(){
    try {
        if (tab === 'sector') {
            const nouSector = {
                nom: document.getElementById('i-sec-nom').value,
                codi_sector: document.getElementById('i-sec-codi').value,
                codi_jaciment: document.getElementById('i-sec-codiJac').value,
                descripcio: document.getElementById('i-sec-descr').value,
                //sincronitzat: false,
                //imatges_urls: []
            };
            await db.collection('sectors').add(nouSector);

        } else if (tab === 'yacimiento') {
            const nouJaciment = {
                nom: document.getElementById('i-jac-nom').value,
                codi_jaciment: document.getElementById('i-jac-codi').value,
                director: document.getElementById('i-jac-director').value,
                coordenada_x: document.getElementById('i-jac-alt').value,
                coordenada_y: document.getElementById('i-jac-lat').value,
                coordenada_z: document.getElementById('i-jac-prof').value,
                descripcio: document.getElementById('i-jac-descr').value,
                //sincronitzat: false,
                //imatges_urls: []
            };
            await db.collection('jaciments').add(nouJaciment);

        } else if (tab === 'ue') {
            const novaUE = {
                codi_ue: document.getElementById('i-ue-codi').value,
                codi_sector: document.getElementById('i-ue-codiSec').value,
                codi_jaciment: document.getElementById('i-ue-codiJac').value,
                codi_intervencio: document.getElementById('i-ue-codiInterv').value,
                tipus_ue: document.getElementById('i-ue-tipus').value,
                textura: document.getElementById('i-ue-text').value,
                color: document.getElementById('i-ue-color').value,
                material: document.getElementById('i-ue-mat').value,
                cronologia: document.getElementById('i-ue-cronolog').value,
                estat_conservacio: document.getElementById('i-ue-estado').value,
                //registrat_per: document.getElementById('i-ue-person').value,
                interpretacio: document.getElementById('i-ue-interpr').value,
                descripcio: document.getElementById('i-ue-descr').value,
                longitud: document.getElementById('i-ue-long').value,
                amplada: document.getElementById('i-ue-ampl').value,
                alcada: document.getElementById('i-ue-alc').value,
                cota_sup: document.getElementById('i-ue-cotaSup').value,
                cota_inf: document.getElementById('i-ue-cotaInf').value,
                relacions: [
                    { tipus: 'igual_a', desti: document.querySelector('.rel-igual_a').value },
                    { tipus: 'cobreix', desti: document.querySelector('.rel-cobreix').value },
                    { tipus: 'cobert_per', desti: document.querySelector('.rel-cobert_per').value },
                    { tipus: 'farceix', desti: document.querySelector('.rel-farceix').value },
                    { tipus: 'farcit_per', desti: document.querySelector('.rel-farcit_per').value },
                    { tipus: 'talla', desti: document.querySelector('.rel-talla').value },
                    { tipus: 'tallat_per', desti: document.querySelector('.rel-tallat_per').value },
                    { tipus: 'recolza', desti: document.querySelector('.rel-recolza').value },
                    { tipus: 'se_li_recolza', desti: document.querySelector('.rel-se_li_recolza').value },
                    { tipus: 'lliura', desti: document.querySelector('.rel-lliura').value },
                    { tipus: 'se_li_lliura', desti: document.querySelector('.rel-se_li_lliura').value }
                ].filter(rel => rel.desti !== ''),
                //sincronitzat: false,
                //imatges_urls: []
            };
            await db.collection('unitats_estratigrafiques').add(novaUE);
        }

        alert("Desat correctament!");
        window.location.assign("libreria.html");

    } catch(error) {
        console.error("Error desant:", error);
        alert("Error en desar la fitxa");
    }
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


