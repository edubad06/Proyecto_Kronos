//DOM
const contenedor = document.getElementById('contenedor-fichas');
const filtroTipo = document.getElementById('filtro-tipo');
const filtroBuscar = document.querySelector('.barra-filtros input[type="text"]');
const filtroFecha = document.getElementById('filtro-fecha');
const menuJac = document.querySelector('.menu-jac');

//VARIABLES
let totesFitxes = []; //array con todas las fichas cargadas

const rol = sessionStorage.getItem("rol");
if (rol !== 'director') {
    menuJac.style.display = 'none';
}

const crearFicha = function(id, titulo, subtitulo, categoria, infoDreta, tab) {
    const ficha = document.createElement('div');
    ficha.classList.add('ficha-lista');

    const divIzq = document.createElement('div');

    const divTitulo = document.createElement('div');
    divTitulo.classList.add('titulo-ficha');
    divTitulo.textContent = titulo;

    const divSubtitulo = document.createElement('div');
    divSubtitulo.classList.add('subtitulo-ficha');
    divSubtitulo.textContent = subtitulo;

    const etiqueta = document.createElement('span');
    etiqueta.classList.add('etiqueta-categoria', categoria);
    etiqueta.textContent = categoria === 'jaciment' ? 'Jaciment' : categoria === 'sector' ? 'Sector' : 'UE';

    divIzq.appendChild(divTitulo);
    divIzq.appendChild(divSubtitulo);
    divIzq.appendChild(etiqueta);

    const divDcha = document.createElement('div');
    divDcha.classList.add('datos-ficha-derecha');
    divDcha.textContent = infoDreta;

    ficha.appendChild(divIzq);
    ficha.appendChild(divDcha);

    ficha.addEventListener('click', function() {
        window.location.assign(`detail.html?tab=${tab}&id=${id}`);
    });

    contenedor.appendChild(ficha);
};
const pintarFitxes = function(fitxes) {
    contenedor.replaceChildren(); // limpio antes de pintar
    fitxes.forEach(f => {
        crearFicha(f.id, f.nom, f.subtitol, f.categoria, f.info, f.tab);
    });
};
const cargarFichas = async function() {
    try {
        //CARGO YACIMIENTOS
        const jaciments = await db.collection('jaciments').get();
        jaciments.forEach(doc => {
            const d = doc.data();
            console.log("DATOS YACIMIENTO: ", d);
            console.log("data de la UE:", d.data, typeof d.data);
            //id, titulo, subtitulo, categoria, infoDreta, tab
            totesFitxes.push({
                id: doc.id,
                tab: 'jaciment',
                nom: d.nom,
                subtitol: d.descripcio,
                categoria: 'jaciment',
                info: `Director/a: ${d.director}`,
                data: d.data || null
            });
            
        });

        //CARGO SECTORES
        const sectors = await db.collection('sectors').get();
        sectors.forEach(doc => {
            const d = doc.data();
            console.log("DATOS SECTOR: ", d);
            //id, titulo, subtitulo, categoria, infoDreta, tab
            totesFitxes.push({
                id: doc.id,
                tab: 'sector',
                nom: d.nom,
                subtitol: d.codi_sector,
                categoria: 'sector',
                info: `Jaciment: ${d.codi_jaciment}`,
                data: d.data || null
            });
        });

        //CARGO UEs
        const ues = await db.collection('unitats_estratigrafiques').get();
        ues.forEach(doc => {
            const d = doc.data();
            console.log("DATOS UE: ", d);
            //id, titulo, subtitulo, categoria, infoDreta, tab
            totesFitxes.push({
                id: doc.id,
                tab: 'ue',
                nom: d.codi_ue,
                subtitol: d.tipus_ue,
                categoria: 'ue',
                info: `Sector: ${d.codi_sector || 'No hay sector'}`,
                data: d.data || null
            });
        });

        //pintamos todo al final
        pintarFitxes(totesFitxes);

    } catch (error) {
        console.error("Error carregant les fitxes:", error);
    }
};


cargarFichas();

//FILTROS
const aplicarFiltres = function() {
    let resultat = [...totesFitxes]; //copio el array original
    console.log("tipus:", filtroTipo.value);
    console.log("ordre:", filtroFecha.value);
    console.log("total fitxes:", totesFitxes.length);
    //filtro por tipo
    const tipus = filtroTipo.value.toLowerCase();
    if (tipus !== 'totes') {
        resultat = resultat.filter(f => f.tab === tipus);
    }
    

    //filtro por nombre
    const text = filtroBuscar.value.toLowerCase();
    if (text.length > 0) {
        resultat = resultat.filter(f => f.nom && f.nom.toLowerCase().includes(text));
    }

    //filtro por fecha/orden
    const ordre = filtroFecha.value;
    if (ordre === 'recent') {
        resultat.sort((a, b) => {
            if (!a.data && !b.data) return 0;
            if (!a.data) return 1;
            if (!b.data) return -1;
            return b.data.toMillis() - a.data.toMillis();
        });
    } else if (ordre === 'antic') {
        resultat.sort((a, b) => {
            if (!a.data && !b.data) return 0;
            if (!a.data) return 1;
            if (!b.data) return -1;
            return a.data.toMillis() - b.data.toMillis();
        });
    }
    pintarFitxes(resultat);
};

//filtro por tipo
filtroTipo.addEventListener('change', function() {
    aplicarFiltres();
});

//filtro buscador por nombre
filtroBuscar.addEventListener('input', function() {
    aplicarFiltres();
});

filtroFecha.addEventListener('change', function() {
    aplicarFiltres();
});
