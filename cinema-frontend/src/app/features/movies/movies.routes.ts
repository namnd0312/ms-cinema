import { Routes } from '@angular/router';
import { MovieListComponent } from './movie-list/movie-list.component';
import { MovieDetailComponent } from './movie-detail/movie-detail.component';

export const MOVIES_ROUTES: Routes = [
  { path: '', component: MovieListComponent },
  { path: ':id', component: MovieDetailComponent }
];
