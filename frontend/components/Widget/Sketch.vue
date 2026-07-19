<template>
  <section class="profile-card">
    <div class="profile-card__content">
      <div class="profile-card__avatar-wrap">
        <NuxtLink to="/">
          <img class="profile-card__avatar" :src="siteSettings.profileAvatarUrl" :alt="siteSettings.profileName" />
        </NuxtLink>
      </div>
      <p class="profile-card__name">{{ siteSettings.profileName }}</p>
      <p class="profile-card__bio">{{ siteSettings.profileBio }}</p>
    </div>
  </section>
</template>

<script setup lang="ts">
const {
  siteSettings
} = useSiteSettings();
</script>

<style lang="scss" scoped>
.profile-card {
  border-radius: 12px;
  position: relative;
  padding: 0;
  background: linear-gradient(
    -25deg,
    var(--card-content-bg),
    var(--card-content-op-deep),
    var(--card-content-bg),
    var(--card-content-op-deep)
  );
  background-size: 400%;
}

.profile-card__content {
  padding: 1rem 1.2rem;
  min-height: 230px;
  position: relative;
  display: flex;
  text-align: center;
  flex-direction: column;
}

.profile-card__name {
  font-weight: 700;
  color: var(--text-highlight-color);
  font-size: 20px;
  line-height: 1;
  margin-bottom: 5px;
  margin-top: 0;
}

.profile-card__bio {
  font-size: 12px;
  color: var(--text-highlight-color);
  opacity: 0.6;
  line-height: 2;
}

.profile-card__avatar {
  width: 118px;
  top: 30px;
  border-radius: 50%;
  opacity: 1;
  filter: none;
  transition: 0.3s;

  &:hover {
    transform: rotate(360deg);
  }
}
</style>
