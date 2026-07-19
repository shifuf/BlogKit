export const useLoginModal = () => {
  const open = useState<boolean>('login-modal-open', () => false);
  return {
    open,
    openLogin: () => {
      open.value = true;
    },
    closeLogin: () => {
      open.value = false;
    }
  };
};
