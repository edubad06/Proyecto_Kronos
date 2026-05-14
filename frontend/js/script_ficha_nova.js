//DOM
const form_ue = document.querySelector("#formulario-ue");
const form_jaciment = document.querySelector("#formulario-yacimiento");
const form_sector = document.querySelector("#formulario-sector");
const btn_guardar = document.querySelector(".boton-accion-guardar");
const btn_fitxes = document.querySelector(".enlace-volver");
const dropZone = document.querySelector(".dropZone");

//variables 
let haCanviat = false; //controla si el usuario ha escrito algo, para avisar al salir
const rol = sessionStorage.getItem("rol");
let map;
let marcador;
let latDef = 41.386978;//coordenadas por defecto: Barcelona  
let longDef = 2.170054; 
let imatgesPendents = []; 
/* es una variable temporal para que el usuario pueda arrastrar imágenes antes de darle a guardar 
(en ese momento todavia no existe en Firestore y por tanto no hay id)*/


//MOSTRAR FORMULARIO SEGÚN PARÁMETRO
/*lee el tab de la URL, oculta todos los formularios, y muestra solo el que corresponde.*/
const params = new URLSearchParams(window.location.search);
const tab = params.get('tab') || 'ue';
document.querySelectorAll('.bloque-pestana').forEach(f => f.style.display = 'none');
const formularioActivo = document.getElementById('formulario-' + tab);
if (formularioActivo) formularioActivo.style.display = 'block';

/*
 * INIT MAPA
 * Inicializa el mapa Leaflet o actualiza su posición si ya existe.
 * Si el mapa ya existe, mueve la vista y el marcador a las nuevas coordenadas.
 * Si no existe, crea el mapa desde cero.
 * Parámetros:
 *   - lat: si es 0 o vacío usa latDef
 *   - long: si es 0 o vacío usa longDef
 */ 
const initMapa = function(lat, long) {
    const latNum = parseFloat(lat) || latDef;
    const longNum = parseFloat(long) || longDef;
    
    if (map) {
        //el mapa ya existe: solo movemos la vista y el marcador
        map.setView([latNum, longNum], 12);
        if (marcador) {
            marcador.setLatLng([latNum, longNum]); // mover marcador existente
        } else {
            marcador = L.marker([latNum, longNum]).addTo(map); // crear si no existe
        }
    } else {
        //primera vez: creamos el mapa y el marcador
        map = L.map('map').setView([latNum, longNum], 12);
        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '&copy; OpenStreetMap contributors'
        }).addTo(map);
        marcador = L.marker([latNum, longNum]).addTo(map);
    }
};

/*
 * INICIALITZAR
 * Se ejecuta al cargar la página. Según el tab activo:
 *   - jaciment: inicializa el mapa y carga los técnicos de Firebase como checkboxes
 *   - sector: carga los jaciments en el select
 *   - ue: carga los sectors en el select
 */
const inicialitzar = async function() { //Es asíncrona porque necesita consultar Firebase.
    if (tab === 'jaciment') {
        // Inicializamos el mapa con coordenadas por defecto
        initMapa(latDef, longDef);

        //cargo los técnicos de Firebase y los muestro como checkboxes
        //para que el director pueda asignarlos como editores del jaciment
        const contenedorEditors = document.getElementById('contenedor-editors');
        const tecnics = await db.collection('usuaris').where('rol', '==', 'tecnic').get();
        tecnics.forEach(doc => {
            const u = doc.data();
            const label = document.createElement('label');
            label.classList.add('etiqueta-checkbox-pildora');
            
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.value = u.email;
            
            const span = document.createElement('span');
            span.textContent = u.nom;
            
            label.appendChild(checkbox);
            label.appendChild(span);
            contenedorEditors.appendChild(label);
        });

        //actualizo el mapa en tiempo real cuando el usuario escribe coordenadas
        const latitud = document.getElementById('i-jac-lat');
        const longitud = document.getElementById('i-jac-long');
        latitud.addEventListener('input', function() {
            initMapa(latitud.value, longitud.value);
        });
        longitud.addEventListener('input', function() {
            initMapa(latitud.value, longitud.value);
        });
    };
    //cargo los yacimientos en la select de sectores
    if (tab === 'sector') {
        const selectJac = document.getElementById('i-sec-codiJac');
        const jaciments = await db.collection('jaciments').get();
        jaciments.forEach(doc => {
            const d = doc.data();

            //si es técnico, solo mostramos los jaciments donde es editor
            if (rol === 'tecnic') {
                const emailUsuari = sessionStorage.getItem("email");
                if (!d.editors || !d.editors.includes(emailUsuari)) return;
            }

            const option = document.createElement('option');
            option.value = d.codi_jaciment;
            option.textContent = d.nom;
            selectJac.appendChild(option);
        });        
    };
    //cargo los sectores en la select de ues
    if (tab === 'ue') {
        const selectSec = document.getElementById('i-ue-codiSec');
        const emailUsuari = sessionStorage.getItem("email");
    
        // Si es técnico, primero obtenemos los jaciments permitidos
        let jacimentsPermesos = [];
        if (rol === 'tecnic') {
            const jaciments = await db.collection('jaciments').get();
            jaciments.forEach(doc => {
                const d = doc.data();
                if (d.editors && d.editors.includes(emailUsuari)) {
                    jacimentsPermesos.push(d.codi_jaciment);
                }
            });
        }

        const sectors = await db.collection('sectors').get();
        sectors.forEach(doc => {
            const d = doc.data();
            const codiSector = d.codi_sector || d.id_sector || doc.id;
            const codiJaciment = d.codi_jaciment || d.id_jaciment || '';
            
            // Si es técnico, solo mostramos sectores de jaciments permitidos
            if (rol === 'tecnic' && !jacimentsPermesos.includes(codiJaciment)) return;
            const option = document.createElement('option');
            option.value = codiSector;
            option.textContent = `${d.nom} (${codiJaciment})`;
            selectSec.appendChild(option);
        });
    }
};
inicialitzar();

/* BOTÓN GUARDAR
 * Recoge los valores del formulario y los guarda en Firestore.
 * Después sincroniza con Oracle y sube las imágenes pendientes a S3.
 */
btn_guardar.addEventListener("click", async function(){
    try {
        if (tab === 'sector') {
            const nouSector = {
                nom: document.getElementById('i-sec-nom').value,
                codi_sector: document.getElementById('i-sec-codi').value,
                codi_jaciment: document.getElementById('i-sec-codiJac').value,
                descripcio: document.getElementById('i-sec-descr').value,
                data: firebase.firestore.Timestamp.now(),
                sincronitzat: false
            };
            const docRef = await db.collection('sectors').add(nouSector);
            sincronitzar(); //llamo a la función para actualizar bd
            for (const file of imatgesPendents) {
                await subirImatge(file, docRef.id, 'sectors');
            }

        } else if (tab === 'jaciment') {        
            //cojo los editores marcados (EMAIL)
            const editors = Array.from(
                document.querySelectorAll('#contenedor-editors input:checked')
            ).map(cb => cb.value);

            const nouJaciment = {
                nom: document.getElementById('i-jac-nom').value,
                codi_jaciment: document.getElementById('i-jac-codi').value,
                director: document.getElementById('i-jac-director').value,
                coordenada_x: document.getElementById('i-jac-long').value,
                coordenada_y: document.getElementById('i-jac-lat').value,
                coordenada_z: document.getElementById('i-jac-prof').value,
                descripcio: document.getElementById('i-jac-descr').value,
                editors: editors, //array
                data: firebase.firestore.Timestamp.now(),
                sincronitzat: false
            };    

            const docRef = await db.collection('jaciments').add(nouJaciment);
            sincronitzar(); //llamo a la función para actualizar bd
            for (const file of imatgesPendents) {
                await subirImatge(file, docRef.id, 'jaciments');
            }

        } else if (tab === 'ue') {
            const novaUE = {
                codi_ue: document.getElementById('i-ue-codi').value,
                codi_sector: document.getElementById('i-ue-codiSec').value,
                tipus_ue: document.getElementById('i-ue-tipus').value,
                textura: document.getElementById('i-ue-text').value,
                color: document.getElementById('i-ue-color').value,
                material: document.getElementById('i-ue-mat').value,
                cronologia: document.getElementById('i-ue-cronolog').value,
                estat_conservacio: document.getElementById('i-ue-estado').value,
                data: firebase.firestore.Timestamp.now(),
                descripcio: document.getElementById('i-ue-descr').value,
                registrat_per: sessionStorage.getItem("uid"),
                sincronitzat: false,
                //recojo las relaciones y filtro las que están vacías
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
            };
            const docRef = await db.collection('unitats_estratigrafiques').add(novaUE);
            sincronitzar(); //llamo a la función para actualizar bd
            for (const file of imatgesPendents) {
                await subirImatge(file, docRef.id, 'unitats_estratigrafiques'); 
            }
        }

        alert("Desat correctament!");
        haCanviat = false; //reseteo
        window.location.assign("libreria.html");

    } catch(error) {
        console.error("Error desant:", error);
        alert("Error en desar la fitxa");
    }
});

// Detectar si el usuario ha escrito algo en cualquier campo
formularioActivo.addEventListener('input', function() {
    haCanviat = true;
});

//botón fitxes - volver
const volver = function (){ 
    //Si el usuario ha hecho cambios, pide confirmación antes de salir
    if (haCanviat) {
        let respuesta = confirm("Tens canvis sense desar. Estàs segur que vols sortir?");
        if (respuesta) window.location.assign("libreria.html");
    } else {
        window.location.assign("libreria.html");
    }
    
};
//Si el usuario ha hecho cambios, pide confirmación antes de salir
document.querySelectorAll('.cabecera-principal a, .cabecera-principal button').forEach(function(element) {
    element.addEventListener("click", function(event) {
        if (haCanviat) {
            event.preventDefault();
            let respuesta = confirm("Tens canvis sense desar. Estàs segur que vols sortir?");
            if (respuesta) {
                haCanviat = false;
                window.location.assign(this.href || "libreria.html");
            }
        }
    });
});

btn_fitxes.addEventListener("click", function(){
    volver();
})

//ARRASTRAR Y SOLTAR IMÁGENES
dropZone.addEventListener("dragover", (event) => {
    event.preventDefault();//para que no abra el documento que arrastramos
});
dropZone.addEventListener("drop", (event) => {
    event.preventDefault();
    const files = event.dataTransfer.files;//para recuperar el fichero en binari
    if (files.length > 0) {
        dropZone.textContent = '';
    };
    Array.from(files).forEach(file => {
        if (file.type.startsWith("image/")) {
            imatgesPendents.push(file); // guardamos para subir después
            const reader = new FileReader();
            reader.onload = (e) => {
                const imatge = crearImatge(e.target.result, function() {
                    // eliminar del array de pendientes
                    imatgesPendents = imatgesPendents.filter(f => f !== file);
                });
                dropZone.appendChild(imatge);
            };
            reader.readAsDataURL(file);
        } else {
            alert(`El fitxer "${file.name}" no és una imatge`);
        }
    });
});




