(() => {
    const body = document.body;
    const openButton = document.querySelector('[data-admin-nav-open]');
    const closeButtons = document.querySelectorAll('[data-admin-nav-close]');

    const closeNavigation = () => body.classList.remove('admin-nav-open');

    openButton?.addEventListener('click', () => body.classList.add('admin-nav-open'));
    closeButtons.forEach((button) => button.addEventListener('click', closeNavigation));
    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape') closeNavigation();
    });
    window.addEventListener('resize', () => {
        if (window.innerWidth >= 992) closeNavigation();
    });
})();
