//DOM
const contenedor = document.getElementById('contenedor-fichas');

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
    etiqueta.textContent = categoria === 'yacimiento' ? 'Jaciment' : categoria === 'sector' ? 'Sector' : 'UE';

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

const cargarFichas = async function() {
    try {
        //CARGO YACIMIENTOS
        const jaciments = await db.collection('jaciments').get();
        jaciments.forEach(doc => {
            const d = doc.data();
            console.log("DATOS YACIMIENTO: ", d);
            //id, titulo, subtitulo, categoria, infoDreta, tab
            crearFicha(doc.id, d.nom, d.descripcio, 'yacimiento', `Director/a: ${d.director}`, 'yacimiento');
        });

        //CARGO SECTORES
        const sectors = await db.collection('sectors').get();
        sectors.forEach(doc => {
            const d = doc.data();
            console.log("DATOS SECTOR: ", d);
            //id, titulo, subtitulo, categoria, infoDreta, tab
            crearFicha(doc.id, d.nom, d.codi_sector, 'sector', `Jaciment: ${d.codi_jaciment}`, 'sector');
        });

        //CARGO UEs
        const ues = await db.collection('unitats_estratigrafiques').get();
        ues.forEach(doc => {
            const d = doc.data();
            console.log("DATOS UE: ", d);
            //id, titulo, subtitulo, categoria, infoDreta, tab
            crearFicha(doc.id, d.codi_ue, d.tipus_ue, 'ue', `Sector: ${d.codi_sector || 'No hay sector'} `, 'ue');
        });

    } catch (error) {
        console.error("Error carregant les fitxes:", error);
    }
};

cargarFichas();