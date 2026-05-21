import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
} from '@angular/core';
import { PlantContextService } from './plant-context.service';

export interface PlantOption {
  id: string;
  name: string;
  code: string;
}

@Component({
  selector: 'app-plant-selector',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [],
  templateUrl: './plant-selector.component.html',
  styleUrl: './plant-selector.component.scss',
})
export class PlantSelectorComponent {
  plants = input<PlantOption[]>([]);

  plantSelected = output<string | null>();

  private readonly plantContext = inject(PlantContextService);

  readonly selectedPlantId = this.plantContext.selectedPlantId;

  readonly shouldShow = computed(() => this.plants().length > 1);

  selectPlant(id: string | null): void {
    this.plantContext.setPlant(id);
    this.plantSelected.emit(id);
  }
}
